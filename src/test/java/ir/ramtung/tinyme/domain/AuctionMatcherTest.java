package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.ChangeMatchingStateHandler;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;


import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
public class AuctionMatcherTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private AuctionMatcher auctionMatcher;
    @Autowired
    ChangeMatchingStateHandler stateHandler;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().lastTradePrice(15000).state(MatchingState.AUCTION).build();
        broker = Broker.builder().brokerId(0).credit(1_000_000L).build();
        shareholder = Shareholder.builder().shareholderId(0).build();
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new Order(1, security, Side.BUY, 445, 16000, broker, shareholder),
                new Order(2, security, Side.BUY, 43, 15900, broker, shareholder),
                new Order(3, security, Side.BUY, 304, 15800, broker, shareholder),
                new Order(4, security, Side.BUY, 304, 15700, broker, shareholder),
                new Order(5, security, Side.BUY, 43, 15500, broker, shareholder),
                new Order(6, security, Side.BUY, 445, 15450, broker, shareholder),
                new Order(7, security, Side.BUY, 526, 15450, broker, shareholder),
                new Order(8, security, Side.BUY, 1000, 15400, broker, shareholder),
                new Order(9, security, Side.SELL, 285, 15430, broker, shareholder),
                new Order(10, security, Side.SELL, 350, 15600, broker, shareholder),
                new Order(11, security, Side.SELL, 350, 15800, broker, shareholder),
                new Order(12, security, Side.SELL, 285, 15810, broker, shareholder),
                new Order(13, security, Side.SELL, 800, 15810, broker, shareholder),
                new Order(14, security, Side.SELL, 340, 15820, broker, shareholder),
                new Order(15, security, Side.SELL, 65, 15820, broker, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void check_matcher_for_list_of_orders() {
        CustomPair pair = security.findOpeningPrice();
        LinkedList<Order> openBuyOrders = security.findOpenOrders(pair.getFirst(), Side.BUY);
        LinkedList<Order> openSellOrders = security.findOpenOrders(pair.getFirst(), Side.SELL);
        int openingPrice = pair.getFirst();

        Order afterFirstTradeOrder1 = new Order(orderBook.findByOrderId(Side.BUY, 1).getOrderId(), orderBook.findByOrderId(Side.BUY, 1).getSecurity(), orderBook.findByOrderId(Side.BUY, 1).getSide(), 160, orderBook.findByOrderId(Side.BUY, 1).getPrice(), orderBook.findByOrderId(Side.BUY, 1).getBroker(), orderBook.findByOrderId(Side.BUY, 1).getShareholder(), orderBook.findByOrderId(Side.BUY, 1).getEntryTime());
        Order afterFirstTradeOrder10 = new Order(orderBook.findByOrderId(Side.SELL, 10).getOrderId(), orderBook.findByOrderId(Side.SELL, 10).getSecurity(), orderBook.findByOrderId(Side.SELL, 10).getSide(), 190, orderBook.findByOrderId(Side.SELL, 10).getPrice(), orderBook.findByOrderId(Side.SELL, 10).getBroker(), orderBook.findByOrderId(Side.SELL, 10).getShareholder(), orderBook.findByOrderId(Side.SELL, 10).getEntryTime());
        Order afterSecondTradeOrder10 = new Order(orderBook.findByOrderId(Side.SELL, 10).getOrderId(), orderBook.findByOrderId(Side.SELL, 10).getSecurity(), orderBook.findByOrderId(Side.SELL, 10).getSide(), 147, orderBook.findByOrderId(Side.SELL, 10).getPrice(), orderBook.findByOrderId(Side.SELL, 10).getBroker(), orderBook.findByOrderId(Side.SELL, 10).getShareholder(), orderBook.findByOrderId(Side.SELL, 10).getEntryTime());
        Order afterFirstTradeOrder3 = new Order(orderBook.findByOrderId(Side.BUY, 3).getOrderId(), orderBook.findByOrderId(Side.BUY, 3).getSecurity(), orderBook.findByOrderId(Side.BUY, 3).getSide(), 157, orderBook.findByOrderId(Side.BUY, 3).getPrice(), orderBook.findByOrderId(Side.BUY, 3).getBroker(), orderBook.findByOrderId(Side.BUY, 3).getShareholder(), orderBook.findByOrderId(Side.BUY, 3).getEntryTime());

        LinkedList<Trade> validTrades = new LinkedList<>();

        validTrades.add(new Trade(orderBook.findByOrderId(Side.BUY, 1).getSecurity(), openingPrice, 285, orderBook.findByOrderId(Side.BUY, 1), orderBook.findByOrderId(Side.SELL, 9)));
        validTrades.add(new Trade(afterFirstTradeOrder1.getSecurity(), openingPrice, 160, afterFirstTradeOrder1, orderBook.findByOrderId(Side.SELL, 10)));
        validTrades.add(new Trade(orderBook.findByOrderId(Side.BUY, 2).getSecurity(), openingPrice, 43, orderBook.findByOrderId(Side.BUY, 2), afterFirstTradeOrder10));
        validTrades.add(new Trade(orderBook.findByOrderId(Side.BUY, 3).getSecurity(), openingPrice, 147, orderBook.findByOrderId(Side.BUY, 3), afterSecondTradeOrder10));
        validTrades.add(new Trade(orderBook.findByOrderId(Side.BUY, 3).getSecurity(), openingPrice, 157, afterFirstTradeOrder3, orderBook.findByOrderId(Side.SELL, 11)));
        MatchResult matchResult = auctionMatcher.match(openBuyOrders, openSellOrders, openingPrice);

        MatchResult validMatchResult = MatchResult.executed(null, validTrades);
        assertThat(matchResult).isEqualTo(validMatchResult);
    }

    @Test
    void check_matcher_for_one_order() {
        orderBook.removeByOrderId(Side.BUY, 1);
        orderBook.removeByOrderId(Side.BUY, 2);
        orderBook.removeByOrderId(Side.BUY, 3);
        orderBook.removeByOrderId(Side.SELL, 9);
        orderBook.removeByOrderId(Side.SELL, 10);
        Order new_order = new Order(16, security, Side.BUY, 450, 15900, broker, shareholder);
        orderBook.enqueue(new_order);
        security.setLastTradePrice(15750);
        CustomPair pair = security.findOpeningPrice();
        LinkedList<Order> openBuyOrders = security.findOpenOrders(pair.getFirst(), Side.BUY);
        LinkedList<Order> openSellOrders = security.findOpenOrders(pair.getFirst(), Side.SELL);
        int openingPrice = pair.getFirst();
        Order afterFirstTradeOrder16 = new Order(orderBook.findByOrderId(Side.BUY, 16).getOrderId(), orderBook.findByOrderId(Side.BUY, 16).getSecurity(), orderBook.findByOrderId(Side.BUY, 16).getSide(), 100, orderBook.findByOrderId(Side.BUY, 16).getPrice(), orderBook.findByOrderId(Side.BUY, 16).getBroker(), orderBook.findByOrderId(Side.BUY, 16).getShareholder(), orderBook.findByOrderId(Side.BUY, 16).getEntryTime());
        LinkedList<Trade> validTrades = new LinkedList<>();
        validTrades.add(new Trade(orderBook.findByOrderId(Side.BUY, 16).getSecurity(), openingPrice, 350, orderBook.findByOrderId(Side.BUY, 16), orderBook.findByOrderId(Side.SELL, 11)));
        validTrades.add(new Trade(afterFirstTradeOrder16.getSecurity(), openingPrice, 100, afterFirstTradeOrder16, orderBook.findByOrderId(Side.SELL, 12)));
        MatchResult matchResult = auctionMatcher.match(openBuyOrders, openSellOrders, openingPrice);
        MatchResult validMatchResult = MatchResult.executed(null, validTrades);
        assertThat(matchResult).isEqualTo(validMatchResult);
    }

    @Test
    void check_matcher_for_one_iceberg_order() {
        orderBook.removeByOrderId(Side.BUY, 1);
        orderBook.removeByOrderId(Side.BUY, 2);
        orderBook.removeByOrderId(Side.BUY, 3);
        orderBook.removeByOrderId(Side.SELL, 9);
        orderBook.removeByOrderId(Side.SELL, 10);
        Order newIcebergOrder = new IcebergOrder(16, security, Side.BUY, 450, 15900, broker, shareholder, 50);
        orderBook.enqueue(newIcebergOrder);
        security.setLastTradePrice(15750);
        CustomPair pair = security.findOpeningPrice();
        LinkedList<Order> openBuyOrders = security.findOpenOrders(pair.getFirst(), Side.BUY);
        LinkedList<Order> openSellOrders = security.findOpenOrders(pair.getFirst(), Side.SELL);
        int openingPrice = pair.getFirst();
        MatchResult matchResult = auctionMatcher.match(openBuyOrders, openSellOrders, openingPrice);
        System.out.println(matchResult);
    }

}