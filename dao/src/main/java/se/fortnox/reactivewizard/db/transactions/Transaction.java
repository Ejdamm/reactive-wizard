package se.fortnox.reactivewizard.db.transactions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import se.fortnox.reactivewizard.db.ConnectionProvider;
import se.fortnox.reactivewizard.db.statement.Statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class Transaction<T> {

    private static final Logger                                      log                 = LoggerFactory.getLogger(Transaction.class);
    private final        ConnectionProvider                          connectionProvider;
    private final        Iterable<Observable<T>>                     daoCalls;
    private final        ConcurrentLinkedQueue<TransactionStatement> statementsToExecute = new ConcurrentLinkedQueue<>();
    private final        Set<TransactionStatement>                   statementsToSubscribe = new HashSet<>();
    private final        AtomicBoolean                               waitingForExecution = new AtomicBoolean(true);
    private              Consumer<Throwable>                         transactionFailed;

    Transaction(ConnectionProvider connectionProvider, Iterable<Observable<T>> daoCalls) {
        this.daoCalls = daoCalls;
        this.connectionProvider = connectionProvider;
    }

    TransactionStatement add(DaoObservable daoObservable) {
        TransactionStatement transactionStatement = new TransactionStatement(this);
        daoObservable.setTransactionStatement(transactionStatement);
        statementsToExecute.add(transactionStatement);
        statementsToSubscribe.add(transactionStatement);
        return transactionStatement;
    }


    public void execute() {
        if (!isAllSubscribed()) {
            // all DaoObservables have not been subscribed yet
            throw new RuntimeException("Transaction execute called before all Observables were subscribed. This should never happen.");
        }

        if (isModifiedAfterCreation()) {
            throw new RuntimeException("Transaction cannot be modified after creation.");
        }

        if (!waitingForExecution.compareAndSet(true, false)) {
            return;
        }

        if (connectionProvider == null) {
            throw new RuntimeException("No Connection Provider found!");
        }

        Connection connection = connectionProvider.get();
        try {
            executeTransaction(connection);
            closeConnection(connection);
            allCompleted();
        } catch (Throwable e) {
            rollback(connection);
            closeConnection(connection);
            allFailed(e);
        }
    }

    private void executeTransaction(Connection connection) throws SQLException {
        connection.setAutoCommit(false);

        for (Batchable statement : batchStatementsWherePossible()) {
            statement.execute(connection);
        }

        connection.commit();
    }

    private LinkedList<Batchable> batchStatementsWherePossible() {
        LinkedList<Batchable> statements = new LinkedList<>();

        for (TransactionStatement statement : statementsToExecute) {
            if (!statements.isEmpty() && statements.peekLast().sameBatch(statement)) {
                Batchable last = statements.pollLast(); // Remove last statement
                statements.add(Batch.batchWrap(last, statement)); // Add in batch with current statement
            } else {
                statements.add(statement);
            }
        }
        return statements;
    }

    private void closeConnection(Connection connection) {
        try {
            connection.setAutoCommit(true);
            connection.close();
        } catch (Exception e) {
            log.error("Error closing connection", e);
        }
    }

    private void allFailed(Throwable throwable) {
        waitingForExecution.set(true);
        for (TransactionStatement transactionStatement : statementsToExecute) {
            final Statement statement = transactionStatement.getStatement();
            try {
                transactionStatement.removeStatement();
                statement.onError(throwable);
            } catch (Exception onErrorException) {
                log.error("onError threw exception", onErrorException);
            }
        }

        if (transactionFailed != null) {
            transactionFailed.accept(throwable);
        }
    }

    private void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (Exception rollbackException) {
            log.error("Rollback failed", rollbackException);
        }
    }

    private void allCompleted() {
        for (TransactionStatement statement : statementsToExecute) {
            try {
                statement.getStatement().onCompleted();
            } catch (Exception onCompletedException) {
                log.error("onCompleted threw exception", onCompletedException);
            }
        }
    }

    private boolean isModifiedAfterCreation() {
        int daoCallsSize = 0;
        if (daoCalls instanceof Collection) {
            daoCallsSize = ((Collection<?>)daoCalls).size();
        } else {
            for (Observable<T> daoCall : daoCalls) {
                daoCallsSize++;
            }
        }

        return daoCallsSize != statementsToExecute.size();
    }

    public boolean isAllSubscribed() {
        return statementsToSubscribe.isEmpty();
    }

    public void markSubscribed(TransactionStatement transactionStatement) {
        statementsToSubscribe.remove(transactionStatement);
    }

    public void markAllSubscribed() {
        statementsToSubscribe.clear();
    }

    public void onTransactionFailed(Consumer<Throwable> transactionFailed) {
        this.transactionFailed = transactionFailed;
    }
}
