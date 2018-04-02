/* Copyright (C) 2013 Interactive Brokers LLC. All rights reserved.  This code is subject to the terms
 * and conditions of the IB API Non-Commercial License or the IB API Commercial License, as applicable. */

package ibdemo.samples.dnhedge;


import com.ib.client.*;
import com.ib.contracts.StkContract;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

class BasicWrapper implements EWrapper {
    private static final int MAX_MESSAGES = 1000000;
    private final static SimpleDateFormat m_df = new SimpleDateFormat("HH:mm:ss");

    // main client
    private EJavaSignal m_signal = new EJavaSignal();
    private EClientSocket m_client = new EClientSocket(this, m_signal);

    // utils
    private long ts;
    private PrintStream m_output;
    private int m_outputCounter = 0;
    private int m_messageCounter;

    protected EClientSocket client() { return m_client; }

    public BasicWrapper() {
        initNextOutput();
        attachDisconnectHook(this);
    }

    public void connect(int clientId) {
        String host = System.getProperty("jts.host");
        host = host != null ? host : "";
        m_client.eConnect(host, 7497, clientId);

        final EReader reader = new EReader(m_client, m_signal);

        reader.start();

        new Thread() {
            public void run() {
                while (m_client.isConnected()) {
                    m_signal.waitForSignal();
                    try {
                        javax.swing.SwingUtilities
                                .invokeAndWait(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            reader.processMsgs();
                                        } catch (IOException e) {
                                            error(e);
                                        }
                                    }
                                });
                    } catch (Exception e) {
                        error(e);
                    }
                }
            }
        }.start();
    }

    public void disconnect() {
        m_client.eDisconnect();
    }

	/* ***************************************************************
	 * AnyWrapper
	 *****************************************************************/

    public void error(Exception e) {
        e.printStackTrace(m_output);
    }

    public void error(String str) {
        m_output.println(str);
    }

    public void error(int id, int errorCode, String errorMsg) {
        logIn("Error id=" + id + " code=" + errorCode + " msg=" + errorMsg);
    }

    public void connectionClosed() {
        m_output.println("--------------------- CLOSED ---------------------");
    }

	/* ***************************************************************
	 * EWrapper
	 *****************************************************************/

    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        logIn("tickPrice");
    }

    public void tickSize(int tickerId, int field, int size) {
        logIn("tickSize");
    }

    public void tickGeneric(int tickerId, int tickType, double value) {
        logIn("tickGeneric");
    }

    public void tickString(int tickerId, int tickType, String value) {
        logIn("tickString");
    }

    public void tickSnapshotEnd(int tickerId) {
        logIn("tickSnapshotEnd");
    }

    public void tickOptionComputation(int tickerId, int field, double impliedVol,
                                      double delta, double optPrice, double pvDividend,
                                      double gamma, double vega, double theta, double undPrice) {
        logIn("tickOptionComputation");
    }

    public void tickEFP(int tickerId, int tickType, double basisPoints,
                        String formattedBasisPoints, double impliedFuture, int holdDays,
                        String futureLastTradeDate, double dividendImpact, double dividendsToLastTradeDate) {
        logIn("tickEFP");
    }

    @Override
    public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {

    }

    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        logIn("openOrder");
    }

    public void openOrderEnd() {
        logIn("openOrderEnd");
    }

    public void updateAccountValue(String key, String value, String currency, String accountName) {
        logIn("updateAccountValue");
    }

    @Override
    public void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {

    }

    public void updateAccountTime(String timeStamp) {
        logIn("updateAccountTime");
    }

    public void accountDownloadEnd(String accountName) {
        logIn("accountDownloadEnd");
    }

    public void nextValidId(int orderId) {
        logIn("nextValidId");
    }

    public void contractDetails(int reqId, ContractDetails contractDetails) {
        logIn("contractDetails");
    }

    public void contractDetailsEnd(int reqId) {
        logIn("contractDetailsEnd");
    }

    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
        logIn("bondContractDetails");
    }

    public void execDetails(int reqId, Contract contract, Execution execution) {
        logIn("execDetails");
    }

    public void execDetailsEnd(int reqId) {
        logIn("execDetailsEnd");
    }

    public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size) {
        logIn("updateMktDepth");
    }

    public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation,
                                 int side, double price, int size) {
        logIn("updateMktDepthL2");
    }

    public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
        logIn("updateNewsBulletin");
    }

    public void managedAccounts(String accountsList) {
        logIn("managedAccounts");
    }

    public void receiveFA(int faDataType, String xml) {
        logIn("receiveFA");
    }

    public void historicalData(int reqId, String date, double open, double high, double low,
                               double close, int volume, int count, double WAP, boolean hasGaps) {
        logIn("historicalData");
    }

    public void scannerParameters(String xml) {
        logIn("scannerParameters");
    }

    public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance,
                            String benchmark, String projection, String legsStr) {
        logIn("scannerData");
    }

    public void scannerDataEnd(int reqId) {
        logIn("scannerDataEnd");
    }

    public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
                            long volume, double wap, int count) {
        logIn("realtimeBar");
    }

    public void currentTime(long millis) {
        logIn("currentTime");
    }

    public void fundamentalData(int reqId, String data) {
        logIn("fundamentalData");
    }

    public void deltaNeutralValidation(int reqId, DeltaNeutralContract underComp) {
        logIn("deltaNeutralValidation");
    }

    public void marketDataType(int reqId, int marketDataType) {
        logIn("marketDataType");
    }

    public void commissionReport(CommissionReport commissionReport) {
        logIn("commissionReport");
    }

    @Override
    public void position(String account, Contract contract, int pos, double avgCost) {

    }


    public void position(String account, Contract contract, double pos, double avgCost) {
        logIn("position");
    }

    public void positionEnd() {
        logIn("positionEnd");
    }

    public void accountSummary( int reqId, String account, String tag, String value, String currency) {
        logIn("accountSummary");
    }

    public void accountSummaryEnd( int reqId) {
        logIn("accountSummaryEnd");
    }

    public void verifyMessageAPI( String apiData) {
        logIn("verifyMessageAPI");
    }

    public void verifyCompleted( boolean isSuccessful, String errorText){
        logIn("verifyCompleted");
    }

    public void verifyAndAuthMessageAPI( String apiData, String xyzChallenge) {
        logIn("verifyAndAuthMessageAPI");
    }

    public void verifyAndAuthCompleted( boolean isSuccessful, String errorText){
        logIn("verifyAndAuthCompleted");
    }

    public void displayGroupList( int reqId, String groups){
        logIn("displayGroupList");
    }

    public void displayGroupUpdated( int reqId, String contractInfo){
        logIn("displayGroupUpdated");
    }

    public void positionMulti( int reqId, String account, String modelCode, Contract contract, double pos, double avgCost) {
        logIn("positionMulti");
    }

    /* ***************************************************************
     * Helpers
     *****************************************************************/
    protected void logIn(String method) {
        m_messageCounter++;
        if (m_messageCounter == MAX_MESSAGES) {
            m_output.close();
            initNextOutput();
            m_messageCounter = 0;
        }
        m_output.println("[W] > " + method);
    }

    protected static void consoleMsg(String str) {
        System.out.println(Thread.currentThread().getName() + " (" + tsStr() + "): " + str);
    }

    protected static String tsStr() {
        synchronized (m_df) {
            return m_df.format(new Date());
        }
    }

    protected static void sleepSec(int sec) {
        sleep(sec * 1000);
    }

    protected static void sleep(int msec) {
        try {
            Thread.sleep(msec);
        } catch (Exception e) { /* noop */ }
    }

    protected void swStart() {
        ts = System.currentTimeMillis();
    }

    protected void swStop() {
        long dt = System.currentTimeMillis() - ts;
        m_output.println("[API]" + " Time=" + dt);
    }

    private void initNextOutput() {
        try {
            m_output = new PrintStream(new File("sysout_" + (++m_outputCounter) + ".log"));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private static void attachDisconnectHook(final BasicWrapper ut) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                ut.disconnect();
            }
        });
    }

    public void connectAck() {
       m_client.startAPI();
    }

}

public class SampleDNHedge extends BasicWrapper {

   public SampleDNHedge() {
   }

    public void contractDetails(int reqId, ContractDetails contractDetails) {
      consoleMsg("contractDetails: " + reqId);

      try {
            int m_underConId  = contractDetails.contract().conid();
            consoleMsg("found conid " + m_underConId);
      }
      catch (Exception e) {
         // will update status and notify main thread
         error (e.toString());
      }
   }

   public void contractDetailsEnd(int reqId) {
      consoleMsg("contractDetailsEnd: " + reqId);

      try {
      }
      catch (Exception e) {
         // will update status and notify main thread
         error (e.toString());
      }
      disconnect();
   }

   public void error(String str) {
      consoleMsg("Error=" + str);
   }

   public void error(int id, int errorCode, String errorMsg) {
      consoleMsg("Error id=" + id + " code=" + errorCode + " msg=" + errorMsg);

      if (errorCode >= 2100 && errorCode < 2200) {
         return;
      }
   }

   /* ***************************************************************
    * Main Method
    *****************************************************************/
   public static void main(String[] args) {
      try {
         SampleDNHedge ut = new SampleDNHedge();
          ut.connect(2);
          Contract contract = new StkContract("IBM");
          contract.currency("USD");
          contract.multiplier("100");
          ut.client().reqContractDetails(1, contract);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
}