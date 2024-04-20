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
//import ir.ramtung.tinyme.messaging.request.UpdateOrderRq;


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
                new StopLimitOrder(11, security, Side.BUY, 300, 15800, broker1, shareholder, 16300),
                new StopLimitOrder(12, security, Side.BUY, 43, 15500, broker1, shareholder, 16350),
                new StopLimitOrder(13, security, Side.BUY, 445, 15450, broker1, shareholder, 16400),
                new StopLimitOrder(14, security, Side.BUY, 526, 15450, broker1, shareholder, 16500),
                new StopLimitOrder(15, security, Side.BUY, 1000, 15400, broker2, shareholder, 16500),
                new StopLimitOrder(16, security, Side.SELL, 350, 15800, broker2, shareholder, 15600),
                new StopLimitOrder(17, security, Side.SELL, 285, 15810, broker1, shareholder, 15550),
                new StopLimitOrder(18, security, Side.SELL, 800, 15810, broker2, shareholder, 15500),
                new StopLimitOrder(19, security, Side.SELL, 340, 15820, broker2, shareholder, 15450),
                new StopLimitOrder(20, security, Side.SELL, 65, 15820, broker2, shareholder, 15400)
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
    void buy_stop_limit_order_rejected_due_to_not_enough_credit() {
        int stopPrice = 50;
        int price = 10000;

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.BUY, 20000,
                price, 2, 1, 0, 0, stopPrice));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(21);
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
                21, LocalDateTime.now(), Side.SELL, 200000,
                price, 2, 1, 0, 0, stopPrice));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(21);
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
                21, LocalDateTime.now(), Side.BUY, 2,
                price, 2, 1, 0, 0, stopPrice));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 21)).isNotEqualTo(null);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
    }

    @Test
    void sell_stop_order_is_added_to_stopOrderBook_sell_queue() {

        int stopPrice = 14000;
        int price = 10000;
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.SELL, 2,
                price, 2, 1, 0, 0, stopPrice));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 21)).isNotEqualTo(null);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
    }

    @Test
    void buy_stop_order_is_activated_and_not_matched() {

        int stopPrice = 14000;
        int price = 15500;
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.BUY, 2,
                price, 2, 1, 0, 0, stopPrice));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 21)).isNotEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 21).getQuantity()).isEqualTo(2);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 21));
    }

    @Test
    void buy_stop_order_is_activated_and_partially_matched() {
        int stopPrice = 14000;
        int price = 15800;
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.BUY, 400,
                price, 1, 1, 0, 0, stopPrice));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 21)).isNotEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 21).getQuantity()).isEqualTo(50);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 21));
    }

    @Test
    void buy_stop_order_is_activated_and_fully_matched() {
        int stopPrice = 14000;
        int price = 15810;
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.BUY, 400,
                price, 1, 1, 0, 0, stopPrice));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 7).getQuantity()).isEqualTo(935);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 21));
    }


    @Test
    void sell_stop_order_is_activated_and_not_matched() {
        int stopPrice = 15200;
        int price = 15750;
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.SELL, 20,
                price, 1, 1, 0, 0, stopPrice));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 21)).isNotEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 21).getQuantity()).isEqualTo(20);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 21));
    }

    @Test
    void sell_stop_order_is_activated_and_partially_matched() {
        int stopPrice = 15200;
        int price = 15700;
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.SELL, 400,
                price, 1, 1, 0, 0, stopPrice));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 21)).isNotEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 21).getQuantity()).isEqualTo(96);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 21));
    }

    @Test
    void sell_stop_order_is_activated_and_fully_matched() {
        int stopPrice = 15200;
        int price = 15500;
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.SELL, 324,
                price, 1, 1, 0, 0, stopPrice));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 2).getQuantity()).isEqualTo(23);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 21));
    }


    @Test
    void update_stop_limit_order_rejected_due_to_not_enough_credit() {
        int orderId = 15;
        int newQuantity = 10000000;
        int stopPrice = 16500;

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", orderId, LocalDateTime.now(), Side.BUY, newQuantity,
                15400, 2, 1, 0, 0, stopPrice));

        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(15);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.BUYER_HAS_NOT_ENOUGH_CREDIT
        );
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 15).getQuantity()).isEqualTo(1000);
    }

    @Test
    void update_stop_limit_order_rejected_due_to_not_enough_position() {
        int orderId = 16;
        int newQuantity = 102_000;
        int stopPrice = 15600;

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", orderId, LocalDateTime.now(), Side.SELL, newQuantity,
                15800, 2, 1, 0, 0, stopPrice));

        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(16);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.SELLER_HAS_NOT_ENOUGH_POSITIONS
        );
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 16).getQuantity()).isEqualTo(350);
    }

    @Test
    void stop_limit_order_with_peaksize_is_rejected(){
        int stopPrice = 15600;
        int price = 10000;

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.BUY, 20000,
                price, 1, 1, 400, 0, stopPrice));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(21);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.INVALID_STOP_PRICE
        );
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(stopOrders.subList(0, 5));
    }

    @Test
    void stop_limit_order_with_min_execution_quantity_is_rejected(){
        int stopPrice = 15600;
        int price = 10000;

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.BUY, 20000,
                price, 1, 1, 0, 100, stopPrice));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(21);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.INVALID_STOP_PRICE
        );
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(stopOrders.subList(0, 5));
    }


}