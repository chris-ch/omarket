import argparse
import logging
import math
import os
from datetime import date

import numpy
import pandas
from matplotlib import pyplot

from meanrevert import MeanReversionStrategy, process_strategy
from pricetools import load_prices


def backtest_strategy(start_date, end_date, symbols, prices_path, lookback_period,
                      step_size, start_equity, max_net_position, max_gross_position,
                      max_risk_scale):
    securities = ['PCX/' + symbol for symbol in symbols]
    prices_by_security = dict()
    close_prices = pandas.DataFrame()
    max_start_date = start_date
    min_end_date = end_date
    for security in securities:
        exchange, security_code = security.split('/')
        prices_df = load_prices(prices_path, exchange, security_code)
        prices_by_security[security] = prices_df
        if max_start_date is not None:
            max_start_date = max(max_start_date, prices_df.index.min())

        else:
            max_start_date = prices_df.index.min()

        if min_end_date is not None:
            min_end_date = min(min_end_date, prices_df.index.max())

        else:
            min_end_date = prices_df.index.max()

        close_prices[security] = prices_df['close adj']

    close_prices.reset_index(inplace=True)
    logging.info('considering date range: %s through %s' % (max_start_date, min_end_date))

    for security in securities:
        prices_by_security[security] = prices_by_security[security][
            (prices_by_security[security].index >= max_start_date) & (prices_by_security[security].index <= min_end_date)]

    warmup_period = 10

    strategy = MeanReversionStrategy(securities, lookback_period)
    backtest_data = process_strategy(securities, strategy, warmup_period, prices_by_security,
                                     step_size=step_size, start_equity=start_equity,
                                     max_net_position=max_net_position,
                                     max_gross_position=max_gross_position,
                                     max_risk_scale=max_risk_scale)
    backtest_summary = backtest_data['summary']
    backtest_summary['portfolio'] = '/'.join(symbols)
    return backtest_data


def chart_backtest(start_date, end_date, securities, prices_path, lookback_period,
                   step_size, start_equity,
                   max_net_position, max_gross_position, max_risk_scale):
    pyplot.style.use('ggplot')
    backtest_result = backtest_strategy(start_date, end_date, securities, prices_path, lookback_period=lookback_period,
                                        step_size=step_size, start_equity=start_equity,
                                        max_net_position=max_net_position,
                                        max_gross_position=max_gross_position,
                                        max_risk_scale=max_risk_scale)
    logging.info('fit quality: %s', fit_quality(backtest_result['equity'] - start_equity))
    backtest_result['equity'].plot()
    backtest_result['net_position'].plot()
    backtest_result['gross_position'].plot()
    pyplot.gca().get_yaxis().get_major_formatter().set_useOffset(False)
    backtest_result['factors'].plot(subplots=True)
    backtest_result['bollinger'].plot(subplots=False)
    pyplot.show()


def fit_quality(df):
    regr_df = df.reset_index()
    day_nanos = 24*60*60*1E9
    nanos = regr_df['date'] - regr_df['date'].min()
    df2 = pandas.DataFrame(data=[nanos.astype(int) / day_nanos, regr_df['equity']]).transpose()
    result = pandas.ols(y=df2['equity'], x=df2['date'], intercept=False)
    return {'p-value F-test': result.f_stat['p-value'], 'r-squared': result.r2, 'p-value x': result.p_value['x']}


def main(args):
    # TODO arg line
    prices_path = os.sep.join(['..', 'data', 'eod'])
    start_date = date(int(args.start_yyyymmdd[:4]), int(args.start_yyyymmdd[4:6]), int(args.start_yyyymmdd[6:8]))
    end_date = date(int(args.end_yyyymmdd[:4]), int(args.end_yyyymmdd[4:6]), int(args.end_yyyymmdd[6:8]))
    if args.display is not None:
        securities = args.display.split('/')
        chart_backtest(start_date, end_date, securities, prices_path, lookback_period=args.lookback_period,
                       step_size=args.step_size, start_equity=args.starting_equity,
                       max_net_position=args.max_net_position,
                       max_gross_position=args.max_gross_position,
                       max_risk_scale=args.max_risk_scale)

    elif args.display_portfolio is not None:
        pyplot.style.use('ggplot')
        backtest_results = dict()
        positions = pandas.DataFrame()
        holdings = pandas.DataFrame()
        fills = pandas.DataFrame()
        target_quantities = list()
        with open(args.display_portfolio) as portfolio_file:
            portfolios = [line.strip().split(',') for line in portfolio_file.readlines() if len(line.strip()) > 0]
            logging.info('loaded portfolios: %s' % portfolios)
            for lookback_period, portfolio in portfolios:
                securities = portfolio.split('/')
                backtest_result = backtest_strategy(start_date, end_date, securities, prices_path, lookback_period=int(lookback_period),
                                                    step_size=args.step_size, start_equity=args.starting_equity,
                                                    max_net_position=args.max_net_position,
                                                    max_gross_position=args.max_gross_position,
                                                    max_risk_scale=args.max_risk_scale)
                positions = pandas.concat([positions, backtest_result['positions']])
                holdings = pandas.concat([holdings, backtest_result['holdings']])
                fills = pandas.concat([fills, backtest_result['fills']])
                if backtest_result['next_target_quantities'] is not None:
                    yahoo_codes = ['PCX/' + code for code in securities]
                    target_quantities += zip(yahoo_codes, backtest_result['next_target_quantities'])

                if 'equity' not in backtest_results:
                    backtest_results['equity'] = backtest_result['equity']

                else:
                    backtest_results['equity'] += backtest_result['equity']

        latest_holdings = holdings.pivot_table(index='date', columns='security', values='quantity', aggfunc=numpy.sum).tail(1).transpose()
        latest_holdings.columns = ['quantity']
        last_nav = backtest_results['equity'].tail(1)['equity'].values[0]
        if args.actual_equity:
            target_nav = args.actual_equity

        else:
            target_nav = last_nav

        scaling_ratio = target_nav / last_nav
        scaled_holdings = latest_holdings * scaling_ratio
        logging.info('stocks for a portfolio worth %s:\n%s' % (target_nav, scaled_holdings.round()))
        target_df = pandas.DataFrame(dict(target_quantities), index=[0]).transpose()
        target_df.columns=['target']
        logging.info('new target quantities:\n%s' % (target_df * scaling_ratio))
        target_trades = (target_df['target'] * scaling_ratio - scaled_holdings).dropna()
        logging.info('trades:\n%s' % target_trades.round())

        equity = backtest_results['equity']
        benchmark = load_prices(prices_path, 'PCX', 'SPY')
        equity_df = pandas.concat([equity, benchmark['close adj']], axis=1).dropna()
        equity_df.columns = ['equity', 'benchmark']
        equity_df['benchmark'] = (equity_df['benchmark'].pct_change() + 1.).cumprod() * equity_df.head(1)['equity'].min()
        equity_df.plot()
        logging.info('fit quality: %s', fit_quality(equity - args.starting_equity))
        by_security_pos = positions.pivot_table(index='date', columns='security', values='position', aggfunc=numpy.sum)
        by_security_pos.plot()

        positions_aggregated_net = positions.groupby('date')['position'].sum()
        positions_aggregated_gross = positions.groupby('date')['position'].agg(lambda x: numpy.abs(x).sum())
        positions_aggregated = pandas.DataFrame(index=positions_aggregated_net.index,
                                                data=numpy.array([positions_aggregated_net, positions_aggregated_gross]).transpose(),
                                                columns=['net', 'gross'])
        positions_aggregated['margin_warning'] = equity / 0.4
        positions_aggregated.plot(subplots=False)

        days_interval = (equity.index[-1] - equity.index[0])
        starting_equity = equity.dropna().head(1)['equity'].values[0]
        ending_equity = equity.dropna().tail(1)['equity'].values[0]
        sharpe_ratio = equity.dropna().pct_change().mean() / equity.dropna().pct_change().std() * math.sqrt(250)
        logging.info('sharpe ratio: %.2f', sharpe_ratio)
        annualized_return = 100 * (numpy.power(ending_equity / starting_equity, 365 / days_interval.days) - 1)
        logging.info('annualized return: %.2f percent' % annualized_return)
        logging.info('fills:\n%s', fills.sort_values('date').set_index(['date', 'security']))
        pyplot.show()

    else:
        # backtest batch
        # TODO arg line
        portfolios_path = os.sep.join(['..', 'data', 'portfolios.csv'])
        with open(portfolios_path) as portfolios_file:
            portfolios = [line.strip().split(',') for line in portfolios_file.readlines()]
            results = list()
            for symbols in portfolios:
                backtest_result = backtest_strategy(start_date, end_date, symbols, prices_path,
                                                    lookback_period=args.lookback_period,
                                                    step_size=args.step_size, start_equity=args.starting_equity,
                                                    max_net_position=args.max_net_position,
                                                    max_gross_position=args.max_gross_position,
                                                    max_risk_scale=args.max_risk_scale)
                backtest_data = fit_quality(backtest_result['equity'] - args.starting_equity)
                backtest_data.update(backtest_result['summary'])
                results.append(backtest_data)

            result_df = pandas.DataFrame(results).set_index('portfolio')
            result_df.to_csv('backtest-results.csv')
            print(result_df)

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format='%(asctime)s:%(name)s:%(levelname)s:%(message)s')
    file_handler = logging.FileHandler('backtest.log', mode='w')
    formatter = logging.Formatter('%(asctime)s:%(name)s:%(levelname)s:%(message)s')
    file_handler.setFormatter(formatter)
    logging.getLogger().addHandler(file_handler)
    logging.info('starting script')
    parser = argparse.ArgumentParser(description='Backtesting prototype.',
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter
                                     )
    parser.add_argument('--start-yyyymmdd', type=str, help='backtest start date', default='20130101')
    parser.add_argument('--end-yyyymmdd', type=str, help='backtest end date', default=date.today().strftime('%Y%m%d'))
    parser.add_argument('--display', type=str, help='display portfolio made of comma-separated securities')
    parser.add_argument('--display-portfolio', type=str, help='display aggregated portfolio from specified file')
    parser.add_argument('--lookback-period', type=int, help='lookback period', default=200)
    parser.add_argument('--step-size', type=int, help='deviation unit measured in number of standard deviations', default=2)
    parser.add_argument('--starting-equity', type=float, help='virtual amount of equity when starting backtest for each strategy step', default=8000)
    parser.add_argument('--actual-equity', type=float, help='total equity available for trading')
    parser.add_argument('--max-net-position', type=float, help='max allowed net position for one step, measured as a fraction of equity', default=0.4)
    parser.add_argument('--max-gross-position', type=float, help='max allowed gross position by step, measured as a fraction of equity', default=2.)
    parser.add_argument('--max-risk-scale', type=int, help='max number of steps', default=3)
    args = parser.parse_args()
    main(args)
