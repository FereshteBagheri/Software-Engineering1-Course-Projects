package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.domain.entity.*;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
import java.time.LocalDateTime;


import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class StopLimitOrderTest {
    private Security security;
    private Broker broker1;
    private Broker broker2;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private StopOrderBook stopOrderBook;
    private List<Order> regularOrders;

    private List<StopLimitOrder> stopOrders;
    @Autowired
    private Matcher matcher;
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

    @BeforeEach
    void setup() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();
        security = Security.builder().isin("ABC").lastTradePrice(15000).build();

        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().shareholderId(1).build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        broker1 = Broker.builder().credit(100000000).brokerId(1).build();
        broker2 = Broker.builder().credit(100000).brokerId(2).build();
        brokerRepository.addBroker(broker1);
        brokerRepository.addBroker(broker2);

        orderBook = security.getOrderBook();
        regularOrders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, broker1, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker1, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker1, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker1, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker2, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker2, shareholder),
                new Order(7, security, Side.SELL, 985, 15810, broker1, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker2, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker2, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, broker2, shareholder)
        );
        regularOrders.forEach(order -> orderBook.enqueue(order));

        stopOrderBook = security.getStopOrderBook();
        stopOrders = Arrays.asList(
                new StopLimitOrder(1, security, Side.BUY, 300, 15800, broker1, shareholder, 15100),
                new StopLimitOrder(2, security, Side.BUY, 43, 15500, broker1, shareholder, 15100),
                new StopLimitOrder(3, security, Side.BUY, 445, 15450, broker1, shareholder, 15800),
                new StopLimitOrder(4, security, Side.BUY, 526, 15450, broker1, shareholder, 16000),
                new StopLimitOrder(5, security, Side.BUY, 1000, 15400, broker2, shareholder, 16100),
                new StopLimitOrder(6, security, Side.SELL, 350, 15800, broker2, shareholder, 16200),
                new StopLimitOrder(7, security, Side.SELL, 285, 15810, broker1, shareholder, 16100),
                new StopLimitOrder(8, security, Side.SELL, 800, 15810, broker2, shareholder, 15900),
                new StopLimitOrder(9, security, Side.SELL, 340, 15820, broker2, shareholder, 15900),
                new StopLimitOrder(10, security, Side.SELL, 65, 15820, broker2, shareholder, 15500)
        );
        stopOrders.forEach(stopOrder -> stopOrderBook.enqueue(stopOrder));
    }
    private void printOrderBook(OrderBook orderBook) {
            System.out.println("Order Book:");
            System.out.println("Buy Orders:");
            for (Order order : orderBook.getBuyQueue()) {
                System.out.println(order.toString());
            }
            System.out.println("Sell Orders:");
            for (Order order : orderBook.getSellQueue()) {
                System.out.println(order.toString());
            }
    }

    private void printStopLimitOrderBook(StopOrderBook stopOrderBook) {
            System.out.println("Stop Limit Order Book:");
            System.out.println("Buy Orders:");
            for (StopLimitOrder order : stopOrderBook.getBuyQueue()) {
                System.out.println(order.toString());
            }
            System.out.println("Sell Orders:");
            for (StopLimitOrder order : stopOrderBook.getSellQueue()) {
                System.out.println(order.toString());
            }
    }

    @Test
    void temp(){
        printOrderBook(orderBook);
        printStopLimitOrderBook(stopOrderBook);
    }



    @Test
    void buy_stop_limit_order_rejected_due_to_not_enough_credit() {
        int stopPrice = 50;
        int price = 10000;

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                11, LocalDateTime.now(), Side.BUY, 20000,
                price, 2, 1, 0, 0, stopPrice));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(11);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.BUYER_HAS_NOT_ENOUGH_CREDIT
        );
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(stopOrders.subList(0, 5));
    }

    @Test
    void sell_stop_limit_order_rejected_due_to_not_enough_positions() {
        int stopPrice = 50;
        int price = 10000;
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                11, LocalDateTime.now(), Side.SELL, 200000,
                price, 2, 1, 0, 0, stopPrice));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(11);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.SELLER_HAS_NOT_ENOUGH_POSITIONS
        );
        assertThat(stopOrderBook.getSellQueue()).isEqualTo(stopOrders.subList(5, 10));
    }

    @Test
    void buy_stop_order_is_added_to_stopOrderBook_buy_queue() {
        int stopPrice = 15050;
        int price = 10000;

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                11, LocalDateTime.now(), Side.BUY, 2,
                price, 2, 1, 0, 0, stopPrice));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 11)).isNotEqualTo(null);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 11));
    }

    @Test
    void sell_stop_order_is_added_to_stopOrderBook_sell_queue() {

        int stopPrice = 14000;
        int price = 10000;
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                11, LocalDateTime.now(), Side.SELL, 2,
                price, 2, 1, 0, 0, stopPrice));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 11)).isNotEqualTo(null);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 11));
    }

    @Test
    void sell_stop_order_is_activated_and_not_matched() {

        int stopPrice = 14000;
        int price = 15500;
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                11, LocalDateTime.now(), Side.BUY, 2,
                price, 2, 1, 0, 0, stopPrice));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 11)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 11)).isNotEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 11).getQuantity()).isEqualTo(2);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 11));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 11));
    }

    @Test // this has problems
    void sell_stop_order_is_activated_and_partially_matched() {

        int stopPrice = 14000;
        int price = 15800;
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                11, LocalDateTime.now(), Side.BUY, 400,
                price, 1, 1, 0, 0, stopPrice));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 11)).isEqualTo(null);
//        assertThat(orderBook.findByOrderId(Side.BUY, 11)).isNotEqualTo(null);
        printOrderBook(orderBook);
        printStopLimitOrderBook(stopOrderBook);
//        assertThat(orderBook.findByOrderId(Side.BUY, 11).getQuantity()).isEqualTo(50);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 11));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 11));
    }

}