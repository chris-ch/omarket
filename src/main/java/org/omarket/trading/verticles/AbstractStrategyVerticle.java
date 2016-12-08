package org.omarket.trading.verticles;

import io.vertx.core.Handler;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Future;
import io.vertx.rxjava.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.eventbus.MessageConsumer;
import org.omarket.trading.MarketData;
import org.omarket.trading.quote.QuoteConverter;
import org.omarket.trading.quote.Quote;

import java.text.ParseException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.omarket.trading.verticles.MarketDataVerticle.IBROKERS_TICKS_STORAGE_PATH;
import static org.omarket.trading.MarketData.createChannelQuote;

/**
 * Created by Christophe on 18/11/2016.
 */

abstract class AbstractStrategyVerticle extends AbstractVerticle implements StrategyProcessor {
    public static final String PARAM_PAST_QUOTES = "pastQuotes";
    private final static Logger logger = LoggerFactory.getLogger(AbstractStrategyVerticle.class);
    private Quote quote;

    private JsonObject parameters = new JsonObject();

    private Map<Integer, JsonObject> contracts = new HashMap<>();

    abstract protected Integer[] getIBrokersCodes();

    /**
     * For how long back the verticle needs to keep past order books.
     *
     * @return lookback period in milliseconds
     */
    abstract protected Integer getLookBackPeriod();

    /*
     *
     */
    abstract protected void init(Integer lookbackPeriod);

    public void updateQuotes(Quote quote) throws ParseException {
        List<JsonObject> quotes = this.getParameters().getJsonArray(PARAM_PAST_QUOTES).getList();
        if (quotes.size() > 0) {
            Quote firstQuote = QuoteConverter.fromJSON(quotes.get(0));
            Quote lastQuote = QuoteConverter.fromJSON(quotes.get(quotes.size() - 1));
            logger.info("quotes range before update: " + firstQuote.getLastModified() + " / " + lastQuote.getLastModified());
        }
        ZonedDateTime lastModified = quote.getLastModified();
        ZonedDateTime expiry = lastModified.minus(getLookBackPeriod(), ChronoUnit.MILLIS);
        this.getParameters().put(PARAM_PAST_QUOTES, new JsonArray());
        for (JsonObject currentQuoteJson : quotes) {
            Quote currentQuote = QuoteConverter.fromJSON(currentQuoteJson);
            if (currentQuote.getLastModified().isAfter(expiry)) {
                this.getParameters().getJsonArray(PARAM_PAST_QUOTES).add(QuoteConverter.toJSON(currentQuote));
            }
        }
        this.getParameters().getJsonArray(PARAM_PAST_QUOTES).add(QuoteConverter.toJSON(quote));
    }

    protected JsonObject getParameters() {
        return parameters;
    }

    protected List<Quote> getPastQuotes() {
        JsonArray quotes = getParameters().getJsonArray(PARAM_PAST_QUOTES);
        List<Quote> quotesList = quotes.getList();
        return quotesList;
    }

    @Override
    public void start() {
        vertx.executeBlocking(new Handler<Future<Object>>() {
            @Override
            public void handle(Future<Object> future) {
                try {
                    JsonArray array = new JsonArray();
                    AbstractStrategyVerticle.this.getParameters().put(PARAM_PAST_QUOTES, array);
                    AbstractStrategyVerticle.this.init(AbstractStrategyVerticle.this.getLookBackPeriod());
                    future.complete();
                } catch (Exception e) {
                    logger.error("failed to initialize strategy", e);
                    future.fail(e);
                }
            }
        }, completed -> {
            logger.info("initialized strategy");
            for (Integer ibCode : getIBrokersCodes()) {
                vertx.executeBlocking(future -> {
                    boolean runBacktest = config().getBoolean("runBacktestFlag", true);
                    if (runBacktest) {
                        logger.info("executing backtest");
                        JsonArray storageDirs = config().getJsonArray(IBROKERS_TICKS_STORAGE_PATH);
                        List<String> dirs = storageDirs.getList();
                        try {
                            MarketData.processBacktest(dirs, ibCode, this);
                            future.complete();
                        } catch (Exception e) {
                            logger.error("backtest failed", e);
                            future.fail(e);
                        }
                    } else {
                        logger.info("skipping backtest");
                        future.complete();
                    }
                }, result -> {
                    logger.info("processing realtime quotes for " + ibCode);
                    // now launching realtime quotes
                    ObservableFuture<Message<JsonObject>> observable = MarketDataVerticle.subscribeProduct(vertx, ibCode);
                    observable.subscribe(contractMessage -> {
                                JsonObject contract = contractMessage.body();
                                vertx.eventBus().send(MarketDataVerticle.ADDRESS_SUBSCRIBE_TICK, contract, mktDataReply -> {
                                    logger.info("subscription succeeded for product: " + ibCode);
                                    contracts.put(ibCode, contract);

                                    // forwards quotes to strategy processor
                                    final String channelProduct = createChannelQuote(ibCode);
                                    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer(channelProduct);
                                    consumer.toObservable().subscribe(message -> {
                                        try {
                                            quote = QuoteConverter.fromJSON(message.body());
                                            logger.info("updated quote: " + quote);
                                            if (contracts.size() != AbstractStrategyVerticle.this.getIBrokersCodes().length || quote == null) {
                                                return;
                                            }
                                            logger.info("processing order book: " + quote);
                                            processQuote(quote, false);
                                            try {
                                                updateQuotes(quote);
                                            } catch (ParseException e) {
                                                logger.error("unable to update quotes", e);
                                            }
                                        } catch (ParseException e) {
                                            logger.error("failed to parse tick data for contract " + contract, e);
                                        }
                                    });
                                });
                            },
                            failure -> {
                                logger.error("failed to retrieve contract details: ", failure);
                            }
                    );
                });
            }
        });
    }

}

