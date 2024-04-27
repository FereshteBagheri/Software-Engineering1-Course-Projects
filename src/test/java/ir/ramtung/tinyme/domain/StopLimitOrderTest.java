package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;

import java.util.LinkedList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
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
                new StopLimitOrder(16, security, Side.SELL, 350, 15800, broker2, shareholder, 14600),
                new StopLimitOrder(17, security, Side.SELL, 285, 15810, broker1, shareholder, 14550),
                new StopLimitOrder(18, security, Side.SELL, 800, 15810, broker2, shareholder, 14500),
                new StopLimitOrder(19, security, Side.SELL, 340, 15820, broker2, shareholder, 14450),
                new StopLimitOrder(20, security, Side.SELL, 65, 15820, broker2, shareholder, 14400)
        );
        stopOrders.forEach(stopOrder -> stopOrderBook.enqueue(stopOrder));
    }

    @Test
    void buy_stop_limit_order_rejected_due_to_not_enough_credit() {
        int stopPrice = 50;
        int price = 10000;
        long previous_credit = broker2.getCredit();

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

        assertThat(broker2.getCredit()).isEqualTo(previous_credit);
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(stopOrders.subList(0, 5));
        assertThat(stopOrderBook.getSellQueue()).isEqualTo(stopOrders.subList(5, 10));
        assertThat(orderBook.getBuyQueue()).isEqualTo(regularOrders.subList(0, 5));
        assertThat(orderBook.getSellQueue()).isEqualTo(regularOrders.subList(5, 10));
    }

    @Test
    void sell_stop_limit_order_rejected_due_to_not_enough_positions() {
        int stopPrice = 50;
        int price = 10000;
        long previous_credit = broker2.getCredit();

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
        assertThat(broker2.getCredit()).isEqualTo(previous_credit);
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(stopOrders.subList(0, 5));
        assertThat(stopOrderBook.getSellQueue()).isEqualTo(stopOrders.subList(5, 10));
        assertThat(orderBook.getBuyQueue()).isEqualTo(regularOrders.subList(0, 5));
        assertThat(orderBook.getSellQueue()).isEqualTo(regularOrders.subList(5, 10));
    }

    @Test
    void buy_stop_order_is_added_to_stopOrderBook_buy_queue() {
        int stopPrice = 15050;
        int price = 10000;
        long previous_credit = broker2.getCredit();

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.BUY, 2,
                price, 2, 1, 0, 0, stopPrice));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        assertThat(broker2.getCredit()).isEqualTo(previous_credit - 10000*2);
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 21)).isNotEqualTo(null);
        assertThat(stopOrderBook.getSellQueue()).isEqualTo(stopOrders.subList(5, 10));
        assertThat(orderBook.getBuyQueue()).isEqualTo(regularOrders.subList(0, 5));
        assertThat(orderBook.getSellQueue()).isEqualTo(regularOrders.subList(5, 10));
    }

    @Test
    void add_stop_order_to_stopOrderBook_does_not_change_other_queues(){

    }

    @Test
    void sell_stop_order_is_added_to_stopOrderBook_sell_queue() {
        int stopPrice = 14000;
        int price = 10000;
        long previous_credit = broker2.getCredit();

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.SELL, 2,
                price, 2, 1, 0, 0, stopPrice));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 21)).isNotEqualTo(null);
        assertThat(broker2.getCredit()).isEqualTo(previous_credit);
    }

    @Test
    void buy_stop_order_is_activated_and_not_matched() {
        int stopPrice = 14000;
        int price = 15500;
        long previous_credit = broker2.getCredit();

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.BUY, 2,
                price, 2, 1, 0, 0, stopPrice));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 21));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 21)).isNotEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 21).getQuantity()).isEqualTo(2);
        assertThat(broker2.getCredit()).isEqualTo(previous_credit - 2*15500);
    }

    @Test
    void buy_stop_order_is_activated_and_partially_matched() {
        int stopPrice = 14000;
        int price = 15800;
        long previous_credit = broker1.getCredit();
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.BUY, 400,
                price, 1, 1, 0, 0, stopPrice));

        assertThat(stopOrderBook.findByOrderId(Side.BUY, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 21)).isNotEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 21).getQuantity()).isEqualTo(50);
        assertThat(broker1.getCredit()).isEqualTo(previous_credit - 400*price);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 21));
    }

    @Test
    void buy_stop_order_is_activated_and_fully_matched() {
        int stopPrice = 14000;
        int price = 15810;
        long broker1_previous_credit = broker1.getCredit();
        long broker2_previous_credit = broker2.getCredit();

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.BUY, 400,
                price, 1, 1, 0, 0, stopPrice));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 21));
        assertThat(broker1.getCredit()).isEqualTo(broker1_previous_credit - 350*15800);
        assertThat(broker2.getCredit()).isEqualTo(broker2_previous_credit + 350*15800);
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 6)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 7).getQuantity()).isEqualTo(935);
    }

    @Test
    void sell_stop_order_is_activated_and_not_matched() {
        int stopPrice = 15200;
        int price = 15750;
        long previous_credit = broker1.getCredit();

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.SELL, 20,
                price, 1, 1, 0, 0, stopPrice));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 21));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 21)).isNotEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 21).getQuantity()).isEqualTo(20);
        assertThat(broker1.getCredit()).isEqualTo(previous_credit);
    }

    @Test
    void sell_stop_order_is_activated_and_partially_matched() {
        int stopPrice = 15200;
        int price = 15700;
        long previous_credit = broker1.getCredit();

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.SELL, 400,
                price, 1, 1, 0, 0, stopPrice));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 21));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 21)).isNotEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 21).getQuantity()).isEqualTo(96);
        assertThat(broker1.getCredit()).isEqualTo(previous_credit + 304*price);
    }

    @Test
    void sell_stop_order_is_activated_and_fully_matched() {
        int stopPrice = 15200;
        int price = 15500;
        long previous_credit = broker1.getCredit();

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.SELL, 324,
                price, 1, 1, 0, 0, stopPrice));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 21));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 21));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 21)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 2).getQuantity()).isEqualTo(23);
        assertThat(broker1.getCredit()).isEqualTo(previous_credit + 304*15700 + 20*15500);
    }

    @Test
    void update_stop_limit_order_quantity_rejected_due_to_not_enough_credit() {
        int newQuantity = 10000000;
        int stopPrice = 16500;
        long previous_credit = broker2.getCredit();

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 15, LocalDateTime.now(), Side.BUY, newQuantity,
                15400, 2, 1, 0, 0, stopPrice));

        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(15);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.BUYER_HAS_NOT_ENOUGH_CREDIT
        );
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 15).getQuantity()).isEqualTo(1000);
        assertThat(broker2.getCredit()).isEqualTo(previous_credit);
    }

    @Test
    void update_stop_limit_order_price_rejected_due_to_not_enough_credit() {
        int newPrice = 10000000;
        int stopPrice = 16500;
        long previous_credit = broker2.getCredit();

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 15, LocalDateTime.now(), Side.BUY, 1000,
                newPrice, 2, 1, 0, 0, stopPrice));

        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(15);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.BUYER_HAS_NOT_ENOUGH_CREDIT
        );
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 15).getPrice()).isEqualTo(15400);
        assertThat(broker2.getCredit()).isEqualTo(previous_credit);
    }

    @Test
    void update_stop_limit_order_rejected_due_to_not_enough_position() {
        int newQuantity = 102000;
        int stopPrice = 15600;
        //check the number of positions stays the same as before

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 16, LocalDateTime.now(), Side.SELL, newQuantity,
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
    void buy_stop_limit_order_with_negative_stop_price_is_rejected(){
        int stopPrice = -15600;
        int price = 10000;

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.BUY, 20000,
                price, 1, 1, 0, 0, stopPrice));

        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(21);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.INVALID_STOP_PRICE
        );
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(stopOrders.subList(0, 5));
        assertThat(orderBook.getBuyQueue()).isEqualTo(regularOrders.subList(0, 5));
    }

    @Test
    void buy_stop_limit_order_with_peaksize_is_rejected(){
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
                Message.INVALID_STOP_LIMIT_ORDER_WITH_PEAKSIZE
        );
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(stopOrders.subList(0, 5));
        assertThat(orderBook.getBuyQueue()).isEqualTo(regularOrders.subList(0, 5));
    }

    @Test
    void sell_stop_limit_order_with_negative_stop_price_is_rejected(){
        int stopPrice = -15600;
        int price = 10000;

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.SELL, 20000,
                price, 1, 1, 0, 0, stopPrice));

        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(21);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.INVALID_STOP_PRICE
        );
        assertThat(stopOrderBook.getSellQueue()).isEqualTo(stopOrders.subList(5, 10));
        assertThat(orderBook.getSellQueue()).isEqualTo(regularOrders.subList(5, 10));
    }

    @Test
    void sell_stop_limit_order_with_peaksize_is_rejected(){
        int stopPrice = 15600;
        int price = 10000;

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.SELL, 20000,
                price, 1, 1, 400, 0, stopPrice));

        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(21);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.INVALID_STOP_LIMIT_ORDER_WITH_PEAKSIZE
        );
        assertThat(stopOrderBook.getSellQueue()).isEqualTo(stopOrders.subList(5, 10));
        assertThat(orderBook.getSellQueue()).isEqualTo(regularOrders.subList(5, 10));
    }

    @Test
    void buy_stop_limit_order_with_min_execution_quantity_is_rejected(){
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
                Message.INVALID_STOP_LIMIT_ORDER_WITH_MIN_EXECUTION_QUANTITY
        );
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(stopOrders.subList(0, 5));
        assertThat(orderBook.getBuyQueue()).isEqualTo(regularOrders.subList(0, 5));
    }

    @Test
    void sell_stop_limit_order_with_min_execution_quantity_is_rejected(){
        int stopPrice = 15600;
        int price = 10000;

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC",
                21, LocalDateTime.now(), Side.SELL, 20000,
                price, 1, 1, 0, 100, stopPrice));

        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(21);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.INVALID_STOP_LIMIT_ORDER_WITH_MIN_EXECUTION_QUANTITY
        );
        assertThat(stopOrderBook.getSellQueue()).isEqualTo(stopOrders.subList(5, 10));
        assertThat(orderBook.getSellQueue()).isEqualTo(regularOrders.subList(5, 10));
    }

   @Test
   void last_trade_activates_some_stop_orders(){
        Order newOrder = new Order(21, security, Side.BUY, 50, 14550, broker1, shareholder);
        orderBook.enqueue(newOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,"ABC"
                , 22, LocalDateTime.now(), Side.SELL, 2368,
                14550, 1, 1, 0, 0, 0));

        assertThat(orderBook.getBuyQueue().isEmpty()).isTrue();
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 16));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 17));
   }

    @Test
    void update_stop_limit_order_does_not_change_priority_by_decrease_quantity() {
        int orderId = 11;
        StopLimitOrder stopOrderTest = (StopLimitOrder)stopOrderBook.findByOrderId(Side.BUY, orderId);
        int newQuantity = 250;
        LinkedList<StopLimitOrder> valid_buyQueue = stopOrderBook.getBuyQueue();
        LinkedList<Order> valid_sellQueue = orderBook.getSellQueue();
        long valid_credit = broker1.getCredit() + (stopOrderTest.getQuantity() - newQuantity) * stopOrderTest.getPrice();

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC",
                orderId, LocalDateTime.now(), Side.BUY, newQuantity,
                15800, 1, 1, 0, 0, 16300));

        assertThat(broker1.getCredit()).isEqualTo(valid_credit);
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(valid_buyQueue);
        assertThat(orderBook.getSellQueue()).isEqualTo(valid_sellQueue);
        verify(eventPublisher).publish(new OrderUpdatedEvent(1, orderId));
    }

    @Test
    void update_stop_limit_order_changes_priority_and_is_not_activated_by_decrease_price() {
        int orderId = 11;
        int newStopPrice = 16370;
        long previous_credit = broker1.getCredit();
        LinkedList<StopLimitOrder> valid_buyQueue = new LinkedList<>();

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC",
                orderId, LocalDateTime.now(), Side.BUY, 300,
                15800, 1, 1, 0, 0, newStopPrice));
        
        valid_buyQueue.add((StopLimitOrder)stopOrderBook.findByOrderId(Side.BUY, 12));
        valid_buyQueue.add((StopLimitOrder)stopOrderBook.findByOrderId(Side.BUY, 11));
        for (int i = 13; i <= 15; i++){
                valid_buyQueue.add((StopLimitOrder)stopOrderBook.findByOrderId(Side.BUY, i));
        }
        
        assertThat(broker1.getCredit()).isEqualTo(previous_credit);
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(valid_buyQueue);
        verify(eventPublisher).publish(new OrderUpdatedEvent(1, orderId));
    }

    @Test
    void update_stop_limit_order_changes_priority_and_is_activated_and_not_matched_by_decrease_price(){
        int orderId = 12;
        int newStopPrice = 14370;
        long previous_credit = broker1.getCredit();
        LinkedList<StopLimitOrder> valid_buyQueue = new LinkedList<>();

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC",
                orderId, LocalDateTime.now(), Side.BUY, 43,
                15500, 1, 1, 0, 0, newStopPrice));
        
        valid_buyQueue.add((StopLimitOrder)stopOrderBook.findByOrderId(Side.BUY, 11));
        for (int i = 13; i <= 15; i++){
                valid_buyQueue.add((StopLimitOrder)stopOrderBook.findByOrderId(Side.BUY, i));
        }

        assertThat(broker1.getCredit()).isEqualTo(previous_credit);
        assertThat(stopOrderBook.getBuyQueue()).isEqualTo(valid_buyQueue);
        assertThat(orderBook.findByOrderId(Side.BUY, orderId)).isNotEqualTo(null);
        verify(eventPublisher).publish(new OrderUpdatedEvent(1, orderId));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, orderId));
    }

    @Test
    void update_stop_limit_order_causes_activation_and_is_partially_matched(){

        //match does not trigger any stop limit orders
    }

    @Test
    void update_stop_limit_order_causes_activation_and_is_fully_matched(){

        // match does not trigger any stop limit orders

    }
    @Test
    void update_stop_limit_order_active_some_other_orders_that_not_match(){ //this is wrong
        long valid_credit_broker1 = broker1.getCredit() - 300* 15800;

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 12, LocalDateTime.now(), Side.BUY, 43,
                15500, 1, 1, 0, 0, 15800));
        verify(eventPublisher).publish(new OrderUpdatedEvent(1, 12));
        verify(eventPublisher, never()).publish(new OrderActivatedEvent(1, 12));
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(2, "ABC", 11, LocalDateTime.now(), Side.BUY, 300,
                15800, 1, 1, 0, 0, 15000));
        assertThat(broker1.getCredit()).isEqualTo(valid_credit_broker1);
        assertThat(orderBook.findByOrderId(Side.SELL, 6).getQuantity()).isEqualTo(50);
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 11)).isEqualTo(null);
        verify(eventPublisher).publish(new OrderUpdatedEvent(2, 11));
        verify(eventPublisher).publish(new OrderActivatedEvent(2, 11));
        verify(eventPublisher).publish(new OrderActivatedEvent(2, 12));
    }

    @Test
    void update_buy_stop_limit_order_activates_it_and_is_matched(){
        long valid_credit_broker1 = broker1.getCredit() - 300* 15800;

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(2, "ABC", 11, LocalDateTime.now(), Side.BUY, 300,
                15800, 1, 1, 0, 0, 15000));

        assertThat(orderBook.findByOrderId(Side.SELL, 6).getQuantity()).isEqualTo(50);
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 11)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 11)).isEqualTo(null);
        verify(eventPublisher).publish(new OrderUpdatedEvent(2, 11));
        verify(eventPublisher).publish(new OrderActivatedEvent(2, 11));
        assertThat(broker1.getCredit()).isEqualTo(valid_credit_broker1);
    }

    @Test
    void update_sell_stop_limit_order_activates_it_and_is_matched(){
        long valid_credit_broker2 = broker2.getCredit() + 304* 15700;

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(2, "ABC", 16, LocalDateTime.now(), Side.SELL, 350,
                15700, 2, 1, 0, 0, 15000));

        assertThat(stopOrderBook.findByOrderId(Side.SELL, 16)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 1)).isEqualTo(null);

        assertThat(orderBook.findByOrderId(Side.SELL, 16).getQuantity()).isEqualTo(46);
        verify(eventPublisher).publish(new OrderUpdatedEvent(2, 16));
        verify(eventPublisher).publish(new OrderActivatedEvent(2, 16));
        assertThat(broker2.getCredit()).isEqualTo(valid_credit_broker2);
    }



}