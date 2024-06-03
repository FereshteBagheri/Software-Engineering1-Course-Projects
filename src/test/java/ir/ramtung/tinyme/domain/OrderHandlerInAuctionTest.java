package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.service.*;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.OpeningPriceEvent;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
import ir.ramtung.tinyme.messaging.request.*;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.verify;

import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.ChangeMatchingStateHandler;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class OrderHandlerInAuctionTest {

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

    @Autowired
    private ContinuousMatcher continuousMatcher;

    @Autowired
    private AuctionMatcher auctionMatcher;


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
                new Order(4, security, Side.BUY, 304, 15700, broker1, shareholder),
                new Order(5, security, Side.BUY, 43, 15500, broker1, shareholder),
                new Order(6, security, Side.BUY, 445, 15450, broker1, shareholder),
                new Order(7, security, Side.BUY, 526, 15450, broker1, shareholder),
                new Order(8, security, Side.BUY, 1000, 15400, broker1, shareholder),

                new Order(11, security, Side.SELL, 350, 15800, broker2, shareholder),
                new Order(12, security, Side.SELL, 285, 15810, broker2, shareholder),
                new Order(13, security, Side.SELL, 800, 15810, broker2, shareholder),
                new Order(14, security, Side.SELL, 340, 15820, broker2, shareholder),
                new Order(15, security, Side.SELL, 65, 15820, broker2, shareholder)
        );
        regularOrders.forEach(order -> orderBook.enqueue(order));

        stopOrderBook = security.getStopOrderBook();
        stopOrders = Arrays.asList(
                new StopLimitOrder(16, security, Side.BUY, 300, 15800, broker1, shareholder, 16300, 10),
                new StopLimitOrder(17, security, Side.BUY, 43, 15500, broker1, shareholder, 16350, 11),
                new StopLimitOrder(18, security, Side.BUY, 445, 15450, broker1, shareholder, 16400, 12),
                new StopLimitOrder(19, security, Side.BUY, 526, 15450, broker1, shareholder, 16500, 13),
                new StopLimitOrder(20, security, Side.BUY, 1000, 15400, broker2, shareholder, 16500, 14),

                new StopLimitOrder(21, security, Side.SELL, 350, 15800, broker2, shareholder, 14600, 15),
                new StopLimitOrder(22, security, Side.SELL, 285, 15810, broker1, shareholder, 14550, 16),
                new StopLimitOrder(23, security, Side.SELL, 800, 15810, broker2, shareholder, 14500, 17),
                new StopLimitOrder(24, security, Side.SELL, 340, 15820, broker2, shareholder, 14450, 18),
                new StopLimitOrder(25, security, Side.SELL, 65, 15820, broker2, shareholder, 14400, 19)
        );
        stopOrders.forEach(stopOrder -> stopOrderBook.enqueue(stopOrder));
    }

    void setupAuctionOrders() {
        orderBook = security.getOrderBook();
        List<Order> auctionOrders = Arrays.asList(
                new Order(1, security, Side.BUY, 445, 16000, broker1, shareholder),
                new Order(2, security, Side.BUY, 43, 15900, broker1, shareholder),
                new Order(3, security, Side.BUY, 304, 15800, broker1, shareholder),

                new Order(9, security, Side.SELL, 285, 15430, broker2, shareholder),
                new Order(10, security, Side.SELL, 350, 15600, broker1, shareholder)
        );
        auctionOrders.forEach(order -> orderBook.enqueue(order));
    }


    @Test
    void openingPrice_is_published_when_new_sell_order_enters() {
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 300, 15820, 2, shareholder.getShareholderId(), 0, 0, 0));
        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 15800, 792));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 200));
    }

    @Test
    void openingPrice_is_published_when_new_iceberg_sell_order_enters() {
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 100, 15800, 2, shareholder.getShareholderId(), 10, 0, 0));
        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 15800, 792));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 200));
    }

    @Test
    void openingPrice_is_published_when_sell_order_is_updated() {
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        orderHandler.handleRequest(EnterOrderRq.createUpdateOrderRq(1, "ABC", 9, LocalDateTime.now(), Side.SELL, 50, 15400, 2, shareholder.getShareholderId(), 0, 0, 0));
        verify (eventPublisher). publish(new OrderUpdatedEvent(1, 9));
        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 15800, 750));
    }

    @Test
    void openingPrice_is_published_when_sell_order_is_deleted() {
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        orderHandler.handleRequest(new DeleteOrderRq(2,"ABC", Side.SELL, 9));
        verify(eventPublisher).publish(new OrderDeletedEvent(2, 9));
        assertThat(orderBook.findByOrderId(Side.SELL, 9)).isEqualTo(null);
        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 15800, 700));
    }

    @Test
    void openingPrice_is_published_when_iceberg_sell_order_is_deleted() {
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 100, 15800, 2, shareholder.getShareholderId(), 10, 0, 0));
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        orderHandler.handleRequest(new DeleteOrderRq(2,"ABC", Side.SELL, 200));
        verify(eventPublisher).publish(new OrderDeletedEvent(2, 200));
        assertThat(orderBook.findByOrderId(Side.SELL, 200)).isEqualTo(null);
        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 15800, 792));
    }

    @Test
    void openingPrice_is_published_when_new_buy_order_enters() {
        security.setMatchingState(MatchingState.AUCTION);
        setupAuctionOrders();
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 18, 15920, 1, shareholder.getShareholderId(), 0, 0, 0));
        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 15800, 810));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 200));
    }

    @Test
    void openingPrice_is_published_when_new_iceberg_buy_order_enters() {
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 100, 15800, 1, shareholder.getShareholderId(), 10, 0, 0));
        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 15800, 892));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 200));
    }

    @Test
    void openingPrice_is_published_when_buy_order_is_updated() {
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        orderHandler.handleRequest(EnterOrderRq.createUpdateOrderRq(1, "ABC", 1, LocalDateTime.now(), Side.BUY, 433, 16000, 1, shareholder.getShareholderId(), 0, 0, 0));
        verify (eventPublisher). publish(new OrderUpdatedEvent(1, 1));
        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 15800, 780));
    }

    @Test
    void openingPrice_is_published_when_buy_order_is_deleted() {
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        orderHandler.handleRequest(new DeleteOrderRq(2,"ABC", Side.BUY, 3));
        verify(eventPublisher).publish(new OrderDeletedEvent(2, 3));
        assertThat(orderBook.findByOrderId(Side.BUY, 3)).isEqualTo(null);
        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 15600, 635));
    }

    @Test
    void openingPrice_is_published_when_iceberg_buy_order_is_deleted() {
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 40, 15700, 1, shareholder.getShareholderId(), 10, 0, 0));
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        orderHandler.handleRequest(new DeleteOrderRq(2,"ABC", Side.BUY, 200));
        verify(eventPublisher).publish(new OrderDeletedEvent(2, 200));
        assertThat(orderBook.findByOrderId(Side.BUY, 200)).isEqualTo(null);
        verify(eventPublisher).publish(new OpeningPriceEvent("ABC", 15800, 792));
    }

    @Test
    void new_order_with_minimum_execution_quantity_is_rejected_in_auction() {
        security.setMatchingState(MatchingState.AUCTION);
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 18, 15920, 1, shareholder.getShareholderId(), 0, 10, 0));

        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(200);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.MIN_EXECUTION_QUANTITY_IN_AUCTION);
    }

    @Test
    void order_with_MEQ_in_order_book_is_updated_in_auction(){
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 400, 15800, 1, shareholder.getShareholderId(), 0, 10, 0));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        assertThat(orderBook.findByOrderId(Side.BUY, 26).getQuantity()).isEqualTo(50);
        security.setMatchingState(MatchingState.AUCTION);
        setupAuctionOrders();
        orderHandler.handleRequest(EnterOrderRq.createUpdateOrderRq(2, "ABC", 26, LocalDateTime.now(), Side.BUY, 400, 15900, 1, shareholder.getShareholderId(), 0, 10, 0));
        verify (eventPublisher). publish(new OrderUpdatedEvent(2, 26));
        assertThat(orderBook.findByOrderId(Side.BUY, 26).getQuantity()).isEqualTo(400);
    }

    @Test
    void order_with_MEQ_in_order_book_is_deleted_in_auction(){
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 400, 15800, 1, shareholder.getShareholderId(), 0, 10, 0));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        assertThat(orderBook.findByOrderId(Side.BUY, 26).getQuantity()).isEqualTo(50);
        security.setMatchingState(MatchingState.AUCTION);
        setupAuctionOrders();
        orderHandler.handleRequest(new DeleteOrderRq(1,"ABC", Side.BUY, 26));
        verify(eventPublisher).publish(new OrderDeletedEvent(1, 26));
        assertThat(orderBook.findByOrderId(Side.BUY, 26)).isEqualTo(null);
    }

    @Test
    void activated_stop_limit_order_is_updated_in_auction(){
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 400, 15800, 1, shareholder.getShareholderId(), 0, 0, 1400));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        assertThat(orderBook.findByOrderId(Side.BUY, 26).getQuantity()).isEqualTo(50);
        security.setMatchingState(MatchingState.AUCTION);
        setupAuctionOrders();
        orderHandler.handleRequest(EnterOrderRq.createUpdateOrderRq(2, "ABC", 26, LocalDateTime.now(), Side.BUY, 400, 15900, 1, shareholder.getShareholderId(), 0, 0, 0));
        verify (eventPublisher). publish(new OrderUpdatedEvent(2, 26));
        assertThat(orderBook.findByOrderId(Side.BUY, 26).getQuantity()).isEqualTo(400);
    }

    @Test
    void activated_stop_limit_order_is_deleted_in_auction(){
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 400, 15800, 1, shareholder.getShareholderId(), 0, 0, 1400));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        assertThat(orderBook.findByOrderId(Side.BUY, 26).getQuantity()).isEqualTo(50);
        security.setMatchingState(MatchingState.AUCTION);
        setupAuctionOrders();
        orderHandler.handleRequest(new DeleteOrderRq(1,"ABC", Side.BUY, 26));
        verify(eventPublisher).publish(new OrderDeletedEvent(1, 26));
        assertThat(orderBook.findByOrderId(Side.BUY, 26)).isEqualTo(null);
    }

    @Test
    void new_stop_limit_order_is_rejected_in_auction() {
        security.setMatchingState(MatchingState.AUCTION);
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 18, 15920, 1, shareholder.getShareholderId(), 0, 0, 14000));

        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(200);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.NEW_STOP_ORDER_IS_NOT_ALLOWED_IN_AUCTION);
    }

    @Test
    void delete_not_activated_stop_limit_order_is_rejected_in_auction() {
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 200, 15920, 1, shareholder.getShareholderId(), 0, 0, 16000));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 26).getQuantity()).isEqualTo(200);
        security.setMatchingState(MatchingState.AUCTION);
        orderHandler.handleRequest(new DeleteOrderRq(1,"ABC", Side.BUY, 26));

        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(26);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.DELETE_STOP_ORDER_NOT_ALLOWED_IN_AUCTION);
    }

    @Test
    void update_not_activated_stop_limit_order_is_rejected_in_auction() {
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 200, 15920, 1, shareholder.getShareholderId(), 0, 0, 16000));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 26).getQuantity()).isEqualTo(200);
        security.setMatchingState(MatchingState.AUCTION);
        orderHandler.handleRequest(EnterOrderRq.createUpdateOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 200, 15900, 1, shareholder.getShareholderId(), 0, 0, 16000));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(26);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.UPDATE_STOP_ORDER_IS_NOT_ALLOWED_IN_AUCTION);
    }

    @Test
    void enter_new_order_works_correctly_in_auction(){
        security.setMatchingState(MatchingState.AUCTION);
        Order order = new Order(26, security, Side.BUY, 100, 15600, broker1, shareholder);
        MatchResult result = auctionMatcher.execute(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(100);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
    }
    @Test
    void new_order_without_enough_credit_is_rejected_in_auction(){
        security.setMatchingState(MatchingState.AUCTION);
        orderHandler.handleRequest(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 180000, 16000, 2, shareholder.getShareholderId(), 0, 0, 0));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(26);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.BUYER_HAS_NOT_ENOUGH_CREDIT);
    }
}
