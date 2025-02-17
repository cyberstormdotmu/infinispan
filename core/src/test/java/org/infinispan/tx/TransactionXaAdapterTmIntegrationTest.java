package org.infinispan.tx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

import java.util.UUID;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

import org.infinispan.commands.CommandsFactory;
import org.infinispan.commons.tx.XidImpl;
import org.infinispan.commons.util.Util;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.context.InvocationContextFactory;
import org.infinispan.context.impl.TransactionalInvocationContextFactory;
import org.infinispan.interceptors.AsyncInterceptorChain;
import org.infinispan.test.TestingUtil;
import org.infinispan.transaction.impl.TransactionCoordinator;
import org.infinispan.transaction.impl.TransactionOriginatorChecker;
import org.infinispan.transaction.tm.EmbeddedBaseTransactionManager;
import org.infinispan.transaction.tm.EmbeddedTransaction;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.transaction.xa.LocalXaTransaction;
import org.infinispan.transaction.xa.TransactionFactory;
import org.infinispan.transaction.xa.TransactionXaAdapter;
import org.infinispan.transaction.xa.XaTransactionTable;
import org.infinispan.util.concurrent.CompletableFutures;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Mircea.Markus@jboss.com
 * @since 4.2
 */
@Test(groups = "unit", testName = "tx.TransactionXaAdapterTmIntegrationTest")
public class TransactionXaAdapterTmIntegrationTest {
   private LocalXaTransaction localTx;
   private TransactionXaAdapter xaAdapter;
   private XidImpl xid;
   private final UUID uuid = Util.threadLocalRandomUUID();
   private TransactionCoordinator txCoordinator;

   @BeforeMethod
   public void setUp() throws XAException {
      Configuration configuration = new ConfigurationBuilder().build();
      XaTransactionTable txTable = new XaTransactionTable();
      txCoordinator = new TransactionCoordinator();


      TestingUtil.inject(txTable, configuration, txCoordinator, TransactionOriginatorChecker.LOCAL);
      txTable.start();
      txTable.startXidMapping();
      TransactionFactory gtf = new TransactionFactory();
      gtf.init(false, false, true, false);
      GlobalTransaction globalTransaction = gtf.newGlobalTransaction(null, false);
      EmbeddedBaseTransactionManager tm = new EmbeddedBaseTransactionManager();
      localTx = new LocalXaTransaction(new EmbeddedTransaction(tm), globalTransaction, false, 1, 0);
      xid = EmbeddedTransaction.createXid(uuid);

      InvocationContextFactory icf = new TransactionalInvocationContextFactory();
      CommandsFactory commandsFactory = mock(CommandsFactory.class);
      AsyncInterceptorChain invoker = mock(AsyncInterceptorChain.class);

      when(invoker.invokeAsync(any(), any())).thenReturn(CompletableFutures.completedNull());

      TestingUtil.inject(txCoordinator, commandsFactory, icf, invoker, txTable, configuration);
      xaAdapter = new TransactionXaAdapter(localTx, txTable);

      xaAdapter.start(xid, 0);
   }

   public void testPrepareOnNonexistentXid() {
      XidImpl xid = EmbeddedTransaction.createXid(uuid);
      try {
         xaAdapter.prepare(xid);
         assert false;
      } catch (XAException e) {
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }
   }

   public void testCommitOnNonexistentXid() {
      XidImpl xid = EmbeddedTransaction.createXid(uuid);
      try {
         xaAdapter.commit(xid, false);
         assert false;
      } catch (XAException e) {
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }
   }

   public void testRollabckOnNonexistentXid() {
      XidImpl xid = EmbeddedTransaction.createXid(uuid);
      try {
         xaAdapter.rollback(xid);
         assert false;
      } catch (XAException e) {
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }
   }

   public void testPrepareTxMarkedForRollback() {
      localTx.markForRollback(true);
      try {
         xaAdapter.prepare(xid);
         assert false;
      } catch (XAException e) {
         assertEquals(XAException.XA_RBROLLBACK, e.errorCode);
      }
   }

   public void testOnePhaseCommitConfigured() throws XAException {
      Configuration configuration = new ConfigurationBuilder().clustering().cacheMode(CacheMode.INVALIDATION_ASYNC).build();
      TestingUtil.inject(txCoordinator, configuration);
      txCoordinator.start();
      assert XAResource.XA_OK == xaAdapter.prepare(xid);
   }

   public void test1PcAndNonExistentXid() {
      Configuration configuration = new ConfigurationBuilder().clustering().cacheMode(CacheMode.INVALIDATION_ASYNC).build();
      TestingUtil.inject(txCoordinator, configuration);
      try {
         XidImpl doesNotExists = EmbeddedTransaction.createXid(uuid);
         xaAdapter.commit(doesNotExists, false);
         assert false;
      } catch (XAException e) {
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }
   }

   public void test1PcAndNonExistentXid2() {
      Configuration configuration = new ConfigurationBuilder().clustering().cacheMode(CacheMode.DIST_SYNC).build();
      TestingUtil.inject(txCoordinator, configuration);
      try {
         XidImpl doesNotExists = EmbeddedTransaction.createXid(uuid);
         xaAdapter.commit(doesNotExists, true);
         assert false;
      } catch (XAException e) {
         assertEquals(XAException.XAER_NOTA, e.errorCode);
      }
   }
}
