package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.ChangeMatchingStateHandler;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.OpeningPriceEvent;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.verify;



import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.ChangeMatchingStateHandler;
import ir.ramtung.tinyme.domain.service.OrderHandler;
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
public class TempTest {
    private Security security;
    private Broker broker1;
    private Broker broker2;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private StopOrderBook stopOrderBook;
    private List<StopLimitOrder> stopOrders;
    private List<Order> regularOrders;


    @Autowired
    EventPublisher eventPublisher;
    @Autowired
    OrderHandler orderHandler;
    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;

    @Autowired
    ChangeMatchingStateHandler stateHandler;

    @BeforeEach
    void setup() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").lastTradePrice(15000).build();
        shareholder = Shareholder.builder().shareholderId(1).build();
        shareholder.incPosition(security, 100_000);
        broker1 = Broker.builder().credit(100000000).brokerId(1).build();
        broker2 = Broker.builder().credit(100000).brokerId(2).build();

        securityRepository.addSecurity(security);
        shareholderRepository.addShareholder(shareholder);
        brokerRepository.addBroker(broker1);
        brokerRepository.addBroker(broker2);

        orderBook = security.getOrderBook();
        regularOrders = Arrays.asList(
                new Order(1, security, Side.BUY, 445, 16000, broker1, shareholder),
                new Order(2, security, Side.BUY, 43, 15900, broker1, shareholder),
                new Order(3, security, Side.BUY, 304, 15800, broker1, shareholder),
                new Order(4, security, Side.BUY, 304, 15700, broker1, shareholder),
                new Order(5, security, Side.BUY, 43, 15500, broker1, shareholder),
                new Order(6, security, Side.BUY, 445, 15450, broker1, shareholder),
                new Order(7, security, Side.BUY, 526, 15450, broker1, shareholder),
                new Order(8, security, Side.BUY, 1000, 15400, broker1, shareholder),



                new Order(9, security, Side.SELL, 285, 15430, broker2, shareholder),
                new Order(10, security, Side.SELL, 350, 15600, broker1, shareholder),
                new Order(11, security, Side.SELL, 350, 15800, broker2, shareholder),
                new Order(12, security, Side.SELL, 285, 15810, broker2, shareholder),
                new Order(13, security, Side.SELL, 800, 15810, broker2, shareholder),
                new Order(14, security, Side.SELL, 340, 15820, broker2, shareholder),
                new Order(15, security, Side.SELL, 65, 15820, broker2, shareholder)
        );
        regularOrders.forEach(order -> orderBook.enqueue(order));

        stopOrderBook = security.getStopOrderBook();
        stopOrders = Arrays.asList(
                new StopLimitOrder(11, security, Side.BUY, 300, 15800, broker1, shareholder, 16300, 10),
                new StopLimitOrder(12, security, Side.BUY, 43, 15500, broker1, shareholder, 16350, 11),
                new StopLimitOrder(13, security, Side.BUY, 445, 15450, broker1, shareholder, 16400, 12),
                new StopLimitOrder(14, security, Side.BUY, 526, 15450, broker1, shareholder, 16500, 13),
                new StopLimitOrder(15, security, Side.BUY, 1000, 15400, broker2, shareholder, 16500, 14),
                new StopLimitOrder(16, security, Side.SELL, 350, 15800, broker2, shareholder, 14600, 15),
                new StopLimitOrder(17, security, Side.SELL, 285, 15810, broker1, shareholder, 14550, 16),
                new StopLimitOrder(18, security, Side.SELL, 800, 15810, broker2, shareholder, 14500, 17),
                new StopLimitOrder(19, security, Side.SELL, 340, 15820, broker2, shareholder, 14450, 18),
                new StopLimitOrder(20, security, Side.SELL, 65, 15820, broker2, shareholder, 14400, 19)
        );
        stopOrders.forEach(stopOrder -> stopOrderBook.enqueue(stopOrder));
    }


    @Test
    void openingPrice_is_published_when_new_sell_order_enters() {
        stateHandler.handleChangeMatchingStateRq(new ChangeMatchingStateRq(1, "ABC", MatchingState.AUCTION));
        assertThat(security.getState()).isEqualTo(MatchingState.AUCTION);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 300, 15820, 2, shareholder.getShareholderId(), 0, 0, 0));
        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 15800, 792));
    }

    @Test
    void openingPrice_is_published_when_new_buy_order_enters() {
        stateHandler.handleChangeMatchingStateRq(new ChangeMatchingStateRq(1, "ABC", MatchingState.AUCTION));
        assertThat(security.getState()).isEqualTo(MatchingState.AUCTION);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 18, 15920, 1, shareholder.getShareholderId(), 0, 0, 0));
        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 15800, 810));
    }
//
//    @Test
//    void openingPrice_is_published_when_new_order_enters_at_auction_empty_sell_queue() {
//        security.setLastTradePrice(15000);
//        stateHandler.handleChangeMatchingStateRq(new ChangeMatchingStateRq(1, "ABC", MatchingState.AUCTION));
//        assertThat(security.getState()).isEqualTo(MatchingState.AUCTION);
//        broker2.increaseCreditBy(300*15450);
//        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 300, 15450, 2, shareholder.getShareholderId(), 0, 0, 0));
//        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 0, 0));
//    }
//
//    @Test
//    void openingPrice_is_published_when_new_order_enters_at_auction() {
//        security.setLastTradePrice(15000);
//        Order order = new Order(1, security, Side.BUY, 300, 12000, broker1, shareholder);
//        security.getOrderBook().enqueue(order);
//        stateHandler.handleChangeMatchingStateRq(new ChangeMatchingStateRq(1, "ABC", MatchingState.AUCTION));
//        assertThat(security.getState()).isEqualTo(MatchingState.AUCTION);
//        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 350, 11000, 2, shareholder.getShareholderId(), 0, 0, 0));
//        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 12000, 300));
//    }




}
