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
        security = Security.builder().isin("ABC").build();
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
                new StopLimitOrder(1, security, Side.BUY, 300, 15800, broker1, shareholder, 10),
                new StopLimitOrder(2, security, Side.BUY, 43, 15500, broker1, shareholder, 20),
                new StopLimitOrder(3, security, Side.BUY, 445, 15450, broker1, shareholder, 30),
                new StopLimitOrder(4, security, Side.BUY, 526, 15450, broker1, shareholder, 40),
                new StopLimitOrder(5, security, Side.BUY, 1000, 15400, broker2, shareholder, 50),
                new StopLimitOrder(6, security, Side.SELL, 350, 15800, broker2, shareholder, 50),
                new StopLimitOrder(7, security, Side.SELL, 285, 15810, broker1, shareholder, 40),
                new StopLimitOrder(8, security, Side.SELL, 800, 15810, broker2, shareholder, 30),
                new StopLimitOrder(9, security, Side.SELL, 340, 15820, broker2, shareholder, 20),
                new StopLimitOrder(10, security, Side.SELL, 65, 15820, broker2, shareholder, 10)
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






}