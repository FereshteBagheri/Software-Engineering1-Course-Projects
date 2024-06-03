package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.service.*;
import ir.ramtung.tinyme.messaging.EventPublisher;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.ChangeMatchingStateHandler;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class MatchingStateChangeTest {
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
    void publish_change_state_from_continuous_to_auction(){
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        assertThat(security.getState()).isEqualTo(MatchingState.AUCTION);
        verify(eventPublisher).publish(new SecurityStateChangedEvent("ABC", MatchingState.AUCTION));
    }

    @Test
    void publish_change_state_from_auction_to_continuous(){
        security.setMatchingState(MatchingState.AUCTION);
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.CONTINUOUS));
        assertThat(security.getState()).isEqualTo(MatchingState.CONTINUOUS);
        verify(eventPublisher).publish(new SecurityStateChangedEvent("ABC", MatchingState.CONTINUOUS));
    }

    @Test
    void publish_change_state_from_continuous_to_continuous(){
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.CONTINUOUS));
        assertThat(security.getState()).isEqualTo(MatchingState.CONTINUOUS);
        verify(eventPublisher).publish(new SecurityStateChangedEvent("ABC", MatchingState.CONTINUOUS));
    }

    @Test
    void publish_change_state_from_auction_to_auction(){
        security.setMatchingState(MatchingState.AUCTION);
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        assertThat(security.getState()).isEqualTo(MatchingState.AUCTION);
        verify(eventPublisher).publish(new SecurityStateChangedEvent("ABC", MatchingState.AUCTION));
    }

    @Test
    void no_trade_is_executed_when_tradeable_quantity_is_zero_in_auction(){
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        verify(eventPublisher, times(0)).publish(any(TradeEvent.class));
        assertThat(orderBook.getBuyQueue()).isEqualTo(regularOrders.subList(0, 5));
        assertThat(orderBook.getSellQueue()).isEqualTo(regularOrders.subList(5, 10));
    }


    @Test
    void activated_buy_stop_orders_are_added_to_order_book_after_auction_and_not_matched(){
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 300, 16000, 1, shareholder.getShareholderId(), 0, 0, 15500));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 27, LocalDateTime.now(), Side.BUY, 350, 15000, 1, shareholder.getShareholderId(), 0, 0, 15600));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        verify (eventPublisher). publish(new OrderAcceptedEvent(2, 27));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 26)).isNotEqualTo(null);
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 27)).isNotEqualTo(null);

        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));

        verify (eventPublisher). publish(new OrderActivatedEvent(1, 26));
        verify (eventPublisher). publish(new OrderActivatedEvent(2, 27));

        assertThat(stopOrderBook.findByOrderId(Side.BUY, 26)).isEqualTo(null);
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 27)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 26).getQuantity()).isEqualTo(300);
        assertThat(orderBook.findByOrderId(Side.BUY, 27).getQuantity()).isEqualTo(350);

    }

    @Test
    void activated_buy_stop_orders_are_added_to_order_book_after_auction_and_matched(){
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 300, 16000, 1, shareholder.getShareholderId(), 0, 0, 15500));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 27, LocalDateTime.now(), Side.BUY, 350, 15900, 1, shareholder.getShareholderId(), 0, 0, 15600));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        verify (eventPublisher). publish(new OrderAcceptedEvent(2, 27));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 26)).isNotEqualTo(null);
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 27)).isNotEqualTo(null);

        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.CONTINUOUS));

        verify (eventPublisher). publish(new OrderActivatedEvent(1, 26));
        verify (eventPublisher). publish(new OrderActivatedEvent(2, 27));

        assertThat(stopOrderBook.findByOrderId(Side.BUY, 26)).isEqualTo(null);
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 27)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 26)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 27)).isEqualTo(null);

    }

    @Test
    void activated_sell_stop_orders_are_added_to_order_book_after_auction_and_not_matched(){
        security.setLastTradePrice(17000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.SELL, 30, 16000, 2, shareholder.getShareholderId(), 0, 0, 16000));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 27, LocalDateTime.now(), Side.SELL, 35, 15000, 2, shareholder.getShareholderId(), 0, 0, 16000));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        verify (eventPublisher). publish(new OrderAcceptedEvent(2, 27));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 26)).isNotEqualTo(null);
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 27)).isNotEqualTo(null);

        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));

        verify (eventPublisher). publish(new OrderActivatedEvent(1, 26));
        verify (eventPublisher). publish(new OrderActivatedEvent(2, 27));

        assertThat(stopOrderBook.findByOrderId(Side.SELL, 26)).isEqualTo(null);
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 27)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 26).getQuantity()).isEqualTo(30);
        assertThat(orderBook.findByOrderId(Side.SELL, 27).getQuantity()).isEqualTo(35);

    }

    @Test
    void activated_sell_stop_orders_are_added_to_order_book_after_auction_and_matched(){
        security.setLastTradePrice(17000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.SELL, 30, 15000, 2, shareholder.getShareholderId(), 0, 0, 16000));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 27, LocalDateTime.now(), Side.SELL, 35, 15000, 2, shareholder.getShareholderId(), 0, 0, 16000));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        verify (eventPublisher). publish(new OrderAcceptedEvent(2, 27));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 26)).isNotEqualTo(null);
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 27)).isNotEqualTo(null);

        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.CONTINUOUS));

        verify (eventPublisher). publish(new OrderActivatedEvent(1, 26));
        verify (eventPublisher). publish(new OrderActivatedEvent(2, 27));

        assertThat(stopOrderBook.findByOrderId(Side.SELL, 26)).isEqualTo(null);
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 27)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 26)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.SELL, 27)).isEqualTo(null);

    }

    @Test
    void buyer_credit_is_correct_from_auction_to_auction_with_activated_orders() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 300, 16000, 1, shareholder.getShareholderId(), 0, 0, 15500));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 26)).isNotEqualTo(null);

        Long initialCredit = broker1.getCredit();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC",MatchingState.AUCTION));
        verify (eventPublisher). publish(new OrderActivatedEvent(1, 26));
        assertThat(broker1.getCredit()).isEqualTo(initialCredit + 445 * 200 + 43 * 100 + 350 * 15800);
    }

    @Test
    void buyer_credit_is_correct_from_auction_to_auction_without_activated_orders() {
        Long initialCredit = broker1.getCredit();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC",MatchingState.AUCTION));
        assertThat(broker1.getCredit()).isEqualTo(initialCredit + 445 * 200 + 43 * 100 + 350 * 15800);
    }

    @Test
    void seller_credit_is_correct_from_auction_to_auction_with_activated_orders() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 300, 16000, 1, shareholder.getShareholderId(), 0, 0, 15500));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 26)).isNotEqualTo(null);

        long initialCredit = broker2.getCredit();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC",MatchingState.AUCTION));
        verify (eventPublisher). publish(new OrderActivatedEvent(1, 26));
        assertThat(broker2.getCredit()).isEqualTo(initialCredit + (157 + 285) * 15800);
    }

    @Test
    void seller_credit_is_correct_from_auction_to_auction_without_activated_orders() {
        long initialCredit = broker2.getCredit();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        assertThat(broker2.getCredit()).isEqualTo(initialCredit + (157 + 285) * 15800);
    }


    @Test
    void buyer_credit_is_correct_from_auction_to_continuous_without_activated_orders() {
        Long initialCredit = broker1.getCredit();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC",MatchingState.CONTINUOUS));
        assertThat(broker1.getCredit()).isEqualTo(initialCredit + 445 * 200 + 43 * 100 + 350 * 15800);
    }

    @Test
    void seller_credit_is_correct_from_auction_to_continuous_without_activated_orders() {
        long initialCredit = broker2.getCredit();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.CONTINUOUS));
        assertThat(broker2.getCredit()).isEqualTo(initialCredit + (157 + 285) * 15800);
    }

    @Test
    void credit_is_correct_from_auction_to_continuous_with_activated_orders(){
        Long broker1initialCredit = broker1.getCredit();
        long broker2InitialCredit = broker2.getCredit();
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 26, LocalDateTime.now(), Side.BUY, 300, 16000, 1, shareholder.getShareholderId(), 0, 0, 15500));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 27, LocalDateTime.now(), Side.BUY, 400, 15850, 1, shareholder.getShareholderId(), 0, 0, 15500));
        verify (eventPublisher). publish(new OrderAcceptedEvent(1, 26));
        verify (eventPublisher). publish(new OrderAcceptedEvent(2, 27));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 26)).isNotEqualTo(null);
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 27)).isNotEqualTo(null);
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC",MatchingState.CONTINUOUS));
        verify (eventPublisher). publish(new OrderActivatedEvent(1, 26));
        verify (eventPublisher). publish(new OrderActivatedEvent(2, 27));

        assertThat(orderBook.findByOrderId(Side.BUY, 26)).isEqualTo(null);
        assertThat(orderBook.findByOrderId(Side.BUY, 27)).isEqualTo(null);
        assertThat(broker1.getCredit()).isEqualTo(broker1initialCredit + 445 * 200 + 43 * 100 + 350 * 15800 - 193*15800 - 507 * 15810);
        assertThat(broker2.getCredit()).isEqualTo(broker2InitialCredit + (157 + 285) * 15800 + 193*15800 + 507 * 15810);
    }

    @Test
    void trade_executed_events_are_published_after_auction(){
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC",MatchingState.CONTINUOUS));
        verify(eventPublisher).publish(new TradeEvent("ABC", 15800, 285, 1, 9));
        verify(eventPublisher).publish(new TradeEvent("ABC", 15800, 160, 1, 10));
        verify(eventPublisher).publish(new TradeEvent("ABC", 15800, 43, 2, 10));
        verify(eventPublisher).publish(new TradeEvent("ABC", 15800, 147, 3, 10));
        verify(eventPublisher).publish(new TradeEvent("ABC", 15800, 157, 3, 11));
    }

    @Test
    void credit_does_not_change_from_continuous_to_auction(){
        long broker2initialCredit = broker2.getCredit();
        long broker1initialCredit = broker1.getCredit();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        assertThat(broker2.getCredit()).isEqualTo(broker2initialCredit);
        assertThat(broker1.getCredit()).isEqualTo(broker1initialCredit);
    }

    @Test
    void order_book_is_correct_after_auction_no_trade_executed(){
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.CONTINUOUS));
        verify(eventPublisher, times(0)).publish(any(TradeEvent.class));
        assertThat(orderBook.getBuyQueue()).isEqualTo(regularOrders.subList(0, 5));
        assertThat(orderBook.getSellQueue()).isEqualTo(regularOrders.subList(5, 10));
    }

    @Test
    void order_book_is_correct_after_auction_some_trades_executed(){
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        setupAuctionOrders();
        stateHandler.handleRequest(new ChangeMatchingStateRq("ABC", MatchingState.CONTINUOUS));
        assertThat(orderBook.getBuyQueue()).isEqualTo( orderBook.getBuyQueue());
        assertThat(orderBook.getSellQueue().peek().getQuantity()).isEqualTo(193);
        assertThat(orderBook.getSellQueue().subList(1,5)).isEqualTo(regularOrders.subList(6, 10));
    }


}
