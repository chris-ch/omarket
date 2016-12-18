package org.omarket.trading.verticles;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import org.omarket.trading.quote.QuoteConverter;
import org.omarket.trading.quote.Quote;
import rx.Observable;
import rx.functions.Action1;

import java.text.ParseException;
import java.util.*;

/**
 * Created by Christophe on 18/11/2016.
 */

abstract class AbstractStrategyVerticle extends AbstractVerticle implements StrategyProcessor {

    private static final String ADDRESS_HISTORICAL_QUOTES_PREFIX = "oot.historicalData.quote";
    private static final String ADDRESS_REALTIME_START_PREFIX = "oot.realtime.start";
    private final static Logger logger = LoggerFactory.getLogger(AbstractStrategyVerticle.class);
    public static final String KEY_PRODUCT_CODES = "productCodes";
    public static final String KEY_REPLY_TO = "replyTo";
    public static final String KEY_COMPLETION_ADDRESS = "completionAddress";

    private JsonObject parameters = new JsonObject();

    abstract protected String[] getProductCodes();

    /*
     *
     */
    abstract protected void init();

    JsonObject getParameters() {
        return parameters;
    }

    private String getHistoricalQuotesAddress() {
        return ADDRESS_HISTORICAL_QUOTES_PREFIX + "." + this.deploymentID();
    }
    private String getRealtimeQuotesAddress() {
        return ADDRESS_REALTIME_START_PREFIX + "." + this.deploymentID();
    }

    @Override
    public void start() {
        Observable<Integer> initStream = vertx.executeBlockingObservable(future -> {
            try {
                init();
                MessageConsumer<JsonObject> historicalDataConsumer = vertx.eventBus().consumer(getHistoricalQuotesAddress());
                Observable<JsonObject> historicalDataStream = historicalDataConsumer.bodyStream().toObservable();
                historicalDataStream.subscribe(
                        new QuoteProcessor(historicalDataConsumer),
                        error -> {
                            logger.error("error occured during backtest", error);
                        });
                MessageConsumer<JsonObject> realtimeStartConsumer = vertx.eventBus().consumer(getRealtimeQuotesAddress());
                Observable<JsonObject> realtimeStartStream = realtimeStartConsumer.bodyStream().toObservable();
                realtimeStartStream.subscribe(message -> {
                    // TODO - enable realtime processing
                    logger.info("unregistering historical data consumer");
                    historicalDataConsumer.unregister();
                    logger.info("starting realtime processing");
                });
                logger.info("initialization completed");
                future.complete();
            } catch (Exception e) {
                logger.error("failed to initialize strategy", e);
                future.fail(e);
            }
        });
        initStream
                .doOnCompleted(() -> { // requesting historical data
                            JsonObject request = new JsonObject();
                            String[] productCodes = getProductCodes();
                            JsonArray codes = new JsonArray();
                            for(String code: productCodes){
                                codes.add(code);
                            }
                            request.put(KEY_PRODUCT_CODES, codes);
                            request.put(KEY_REPLY_TO, getHistoricalQuotesAddress());
                            request.put(KEY_COMPLETION_ADDRESS, getRealtimeQuotesAddress());
                            logger.info("requesting historical data for products: " + Arrays.asList(productCodes) + " on address: " + HistoricalDataVerticle.ADDRESS_PROVIDE_HISTORY);
                            vertx.eventBus().send(HistoricalDataVerticle.ADDRESS_PROVIDE_HISTORY, request);
                        })
                .doOnError(error -> {
                    logger.error("initialization step failed for " + this.deploymentID());
                })
                .subscribe();
    }

    private class QuoteProcessor implements Action1<JsonObject> {

        final MessageConsumer<JsonObject> consumer;
        final Map<String, Quote> quotes;

        QuoteProcessor(MessageConsumer<JsonObject> consumer) {
            this.consumer = consumer;
            this.quotes = new HashMap<>();
        }

        @Override
        public void call(JsonObject quoteJson) {
                try {
                    Quote quote = QuoteConverter.fromJSON(quoteJson);
                    String productCode = quote.getProductCode();
                    quotes.put(productCode, quote);
                    logger.debug("forwarding order book to implementing strategy: " + quote);
                    processQuotes(quotes);
                } catch (ParseException e) {
                    logger.error("failed to parse tick data from " + quoteJson, e);
                }
        }
    }

}

