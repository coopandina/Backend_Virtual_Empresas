package apiVirtualEmpresa.apiVirtualEmpresa.login.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service

public class TokenExpirationService {
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    public TokenExpirationService(EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.transactionManager = transactionManager;
    }

    public void programarExpiracionToken(String clienCedula, String tokenTemp, String codeMessage) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        executor.schedule(() -> {
            transactionTemplate.execute(status -> {
                try {
                    String sqlUpdate = "UPDATE vircodaccess SET codaccess_estado = '0' " +
                            "WHERE codaccess_cedula = :cedula " +
                            "AND codaccess_codigo_temporal = :token " +
                            "AND codsms_codigo = :codeMessage  " +
                            "AND codaccess_estado = '1' ";

                    Query query = entityManager.createNativeQuery(sqlUpdate);
                    query.setParameter("cedula", clienCedula);
                    query.setParameter("token", tokenTemp);
                    query.setParameter("codeMessage", codeMessage);
                    query.executeUpdate();
                    return null;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    e.printStackTrace();
                    return null;
                } finally {
                    executor.shutdown();
                }
            });
        }, 4, TimeUnit.MINUTES);
    }

}
