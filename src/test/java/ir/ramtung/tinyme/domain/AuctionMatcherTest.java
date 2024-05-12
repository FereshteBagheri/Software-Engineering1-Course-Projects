package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import ch.qos.logback.core.joran.action.Action;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
public class AuctionMatcherTest {
    @Autowired
    OrderHandler orderHandler;
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private AuctionMatcher auctionMatcher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().lastTradePrice(15000).build();
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
    void check_matcher(){
        CustomPair pair = security.findOpeningPrice();
        // BuyOrder List -> 1, 2, 3
        LinkedList<Order> openBuyOrders = security.findOpenOrders(pair.getFirst(), Side.BUY);
        // SellOrder List -> 9, 10, 11
        LinkedList<Order> openSellOrders = security.findOpenOrders(pair.getFirst(), Side.SELL);
        // Exchanged Quantity = 792
        // Opening Price = 15800
        int openingPrice = pair.getFirst();
        LinkedList<Trade> validTrades = new LinkedList<>();

        validTrades.add(new Trade(orderBook.findByOrderId(Side.BUY, 1).getSecurity(), openingPrice, 285, orderBook.findByOrderId(Side.BUY, 1), orderBook.findByOrderId(Side.SELL, 9)));
        //update: orderId = 1, quantity = 160
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1, LocalDateTime.now(), Side.BUY, 160, 15430, 0, 0, 0, 0, 0));
        validTrades.add(new Trade(orderBook.findByOrderId(Side.BUY, 1).getSecurity(), openingPrice, 160, orderBook.findByOrderId(Side.BUY, 1), orderBook.findByOrderId(Side.SELL, 10)));
        //update: orderId = 10, quantity = 190
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(2, security.getIsin(), 10, LocalDateTime.now(), Side.SELL, 190, 15600, 0, 0, 0, 0, 0));
        validTrades.add(new Trade(orderBook.findByOrderId(Side.BUY, 2).getSecurity(), openingPrice, 43, orderBook.findByOrderId(Side.BUY, 2), orderBook.findByOrderId(Side.SELL, 10)));
        //update: orderId = 10, quantity = 147
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(3, security.getIsin(), 10, LocalDateTime.now(), Side.SELL, 147, 15600, 0, 0, 0, 0, 0));
        validTrades.add(new Trade(orderBook.findByOrderId(Side.BUY, 3).getSecurity(), openingPrice, 147, orderBook.findByOrderId(Side.BUY, 3), orderBook.findByOrderId(Side.SELL, 10)));
        //update: orderId = 3, quantity = 157
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(4, security.getIsin(), 3, LocalDateTime.now(), Side.BUY, 157, 15800, 0, 0, 0, 0, 0));
        validTrades.add(new Trade(orderBook.findByOrderId(Side.BUY, 3).getSecurity(), openingPrice, 157, orderBook.findByOrderId(Side.BUY, 3), orderBook.findByOrderId(Side.SELL, 11)));

        MatchResult matchResult = auctionMatcher.match(openBuyOrders, openSellOrders, openingPrice);
        System.out.println(matchResult);

        MatchResult validMatchResult = MatchResult.executed(null, validTrades);
        System.out.println("khar");

        System.out.println(validMatchResult);
        assertThat(matchResult).isEqualTo(validMatchResult);
    }

    
}
