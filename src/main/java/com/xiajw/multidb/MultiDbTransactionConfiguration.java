package com.xiajw.multidb;

import com.atomikos.icatch.jta.UserTransactionImp;
import com.atomikos.icatch.jta.UserTransactionManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.jta.JtaTransactionManager;

import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

@EnableTransactionManagement//开启事务支持
@ConditionalOnClass(TransactionManager.class)//classpath路径中有这个类才构建这个bean
public class MultiDbTransactionConfiguration {

    @Bean(
            name = "atomikosTransactionManager"
    )
    public TransactionManager atomikosTransactionManager(){
        UserTransactionManager userTransactionManager = new UserTransactionManager();
        userTransactionManager.setForceShutdown(false);
        return userTransactionManager;
    }

    @Bean(
            name = {"userTransaction"}
    )
    public UserTransaction userTransaction() throws Throwable {
        UserTransactionImp userTransactionImp = new UserTransactionImp();
        userTransactionImp.setTransactionTimeout(10000);
        return userTransactionImp;
    }

    @Bean(
            name = {"txManager"}
    )
    @DependsOn({"userTransaction", "atomikosTransactionManager"})
    public PlatformTransactionManager transactionManager() throws Throwable {
        UserTransaction userTransaction = this.userTransaction();
        TransactionManager atomikosTransactionManager = this.atomikosTransactionManager();
        return new JtaTransactionManager(userTransaction,atomikosTransactionManager);
    }
}
