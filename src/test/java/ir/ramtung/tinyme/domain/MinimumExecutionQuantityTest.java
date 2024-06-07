package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.ContinuousMatcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class MinimumExecutionQuantityTest {
    private Security security;
    private Broker broker1;
    private Broker broker2;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private ContinuousMatcher continuousMatcher;
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
    void setupOrderBook() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").lastTradePrice(15750).build();

        shareholder = Shareholder.builder().shareholderId(1).build();
        shareholder.incPosition(security, 100_000);

        broker1 = Broker.builder().credit(100000000).brokerId(1).build();
        broker2 = Broker.builder().credit(100000).brokerId(2).build();

        shareholderRepository.addShareholder(shareholder);
        securityRepository.addSecurity(security);
        brokerRepository.addBroker(broker1);
        brokerRepository.addBroker(broker2);

        orderBook = security.getOrderBook();
        orders = Arrays.asList(
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
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void create_enter_order_request_with_minimum() {
        EnterOrderRq newReq = EnterOrderRq.createNewOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0, 10, 0);
        assertThat(newReq.getMinimumExecutionQuantity()).isEqualTo(10);
    }

    @Test
    void create_enter_order_request_without_minimum() {
        EnterOrderRq newReq = EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0, 0, 0);
        assertThat(newReq.getMinimumExecutionQuantity()).isEqualTo(0);
        // to do: this is pointless after removing previous constructor
    }

    @Test
    void create_update_order_request_with_minimum() {
        EnterOrderRq newReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0, 10, 0);
        assertThat(newReq.getMinimumExecutionQuantity()).isEqualTo(10);
    }

    @Test
    void create_update_order_request_without_minimum() {
        EnterOrderRq newReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0, 0, 0);
        assertThat(newReq.getMinimumExecutionQuantity()).isEqualTo(0);
        // to do: this is pointless after removing previous constructor
    }

    @Test
    void new_order_req_with_minimum_execution_quantity_enters_orderBook() {
        EnterOrderRq newReq = EnterOrderRq.createNewOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.BUY, 490,
                15800, 1, 1, 0, 10, 0);
        assertThatNoException().isThrownBy(() -> orderHandler.handleRequest(newReq));
        assertThat(orderBook.getBuyQueue().peek().getOrderId()).isEqualTo(11);
        assertThat(orderBook.getBuyQueue().peek().getQuantity()).isEqualTo(140);
    }

    @Test
    void new_order_req_with_minimum_execution_quantity_is_rejected() {
        EnterOrderRq newReq = EnterOrderRq.createNewOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.BUY, 490,
                15800, 1, 1, 0, 400, 0);
        assertThatNoException().isThrownBy(() -> orderHandler.handleRequest(newReq));
        assertThat(orderBook.findByOrderId(Side.BUY, 11)).isEqualTo(null);
        assertThat(orderBook.getSellQueue()).isEqualTo(orders.subList(5, 10));
        assertThat(orderBook.getBuyQueue()).isEqualTo(orders.subList(0, 5));
    }

    @Test
    void validate_negative_minimum_execution_quantity_fails() {
        EnterOrderRq newReq = EnterOrderRq.createNewOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0, -10, 0);
        orderHandler.handleRequest(newReq);
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getErrors()).containsOnly(Message.INVALID_MINIMUM_EXECUTION_QUANTITY);
    }

    @Test
    void validate_minimum_execution_quantity_greater_than_total_quantity_fails() {
        EnterOrderRq newReq = EnterOrderRq.createNewOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.BUY, 490,
                15450, 1, 1, 0, 500, 0);
        orderHandler.handleRequest(newReq);
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getErrors()).containsOnly(Message.INVALID_MINIMUM_EXECUTION_QUANTITY);
    }

    @Test
    void buy_order_with_minimum_execution_quantity_completely_matched() {
        Order new_order = new Order(11, security, Side.BUY,
                330, 15800, broker1, shareholder,
                LocalDateTime.now(), 40, false);
        MatchResult result = continuousMatcher.execute(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
        assertThat(orderBook.findByOrderId(Side.BUY, 11)).isEqualTo(null);
    }

    @Test
    void sell_order_with_minimum_execution_quantity_completely_matched() {
        Order new_order = new Order(11, security, Side.SELL,
                300, 15600, broker2, shareholder,
                LocalDateTime.now(), 40, false);
        MatchResult result = continuousMatcher.execute(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
        assertThat(orderBook.findByOrderId(Side.SELL, 11)).isEqualTo(null);
    }

    @Test
    void buy_order_partially_matched_with_minimum_execution_quantity() {
        Order new_order = new Order(11, security, Side.BUY,
                400, 15800, broker1, shareholder,
                LocalDateTime.now(), 350, false);
        MatchResult result = continuousMatcher.execute(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(50);
        assertThat(orderBook.getBuyQueue().peek()).isEqualTo(result.remainder());
        assertThat(orderBook.findByOrderId(Side.BUY, 11)).isNotEqualTo(null);
    }

    @Test
    void sell_order_partially_matched_with_minimum_execution_quantity() {
        Order new_order = new Order(11, security, Side.SELL,
                400, 15700, broker1, shareholder,
                LocalDateTime.now(), 304, false);
        MatchResult result = continuousMatcher.execute(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(96);
        assertThat(orderBook.getSellQueue().peek()).isEqualTo(result.remainder());
        assertThat(orderBook.findByOrderId(Side.SELL, 11)).isNotEqualTo(null);
    }

    @Test
    void buy_order_partially_matched_with_greater_than_minimum_execution_quantity() {
        Order new_order = new Order(11, security, Side.BUY,
                400, 15800, broker1, shareholder,
                LocalDateTime.now(), 290, false);
        MatchResult result = continuousMatcher.execute(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(50);
        assertThat(orderBook.getBuyQueue().peek()).isEqualTo(result.remainder());
        assertThat(orderBook.findByOrderId(Side.BUY, 11)).isNotEqualTo(null);
    }

    @Test
    void sell_order_partially_matched_with_greater_than_minimum_execution_quantity() {
        Order new_order = new Order(11, security, Side.SELL,
                400, 15700, broker1, shareholder,
                LocalDateTime.now(), 300, false);
        MatchResult result = continuousMatcher.execute(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(96);
        assertThat(orderBook.getSellQueue().peek()).isEqualTo(result.remainder());
        assertThat(orderBook.findByOrderId(Side.SELL, 11)).isNotEqualTo(null);
    }

    @Test
    void buy_order_with_minimum_execution_quantity_fails() {
        Order new_order = new Order(11, security, Side.BUY,
                400, 15800, broker1, shareholder,
                LocalDateTime.now(), 360, false);
        MatchResult result = continuousMatcher.execute(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.MINIMUM_NOT_MATCHED);
        assertThat(result.remainder()).isEqualTo(null);
        assertThat(orderBook.getSellQueue()).isEqualTo(orders.subList(5, 10));
        assertThat(orderBook.getBuyQueue()).isEqualTo(orders.subList(0, 5));
    }

    @Test
    void sell_order_with_minimum_execution_quantity_fails() {
        Order new_order = new Order(11, security, Side.SELL,
                400, 15600, broker2, shareholder,
                LocalDateTime.now(), 360, false);
        MatchResult result = continuousMatcher.execute(new_order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.MINIMUM_NOT_MATCHED);
        assertThat(result.remainder()).isEqualTo(null);
        assertThat(orderBook.getSellQueue()).isEqualTo(orders.subList(5, 10));
        assertThat(orderBook.getBuyQueue()).isEqualTo(orders.subList(0, 5));
    }

    @Test
    void set_minimum_quantity_executed_after_minimum_execution_quantity_matched_works() {
        Order new_order = new Order(11, security, Side.BUY,
                400, 15800, broker1, shareholder,
                LocalDateTime.now(), 40, false);
        MatchResult result = continuousMatcher.execute(new_order);
        assertThat(result.remainder().isMinimumQuantityExecuted()).isEqualTo(true);
    }

    @Test
    void update_order_request_with_same_minimum_works() {
        Order new_order = new Order(11, security, Side.SELL,
                504, 15700, broker1, shareholder,
                LocalDateTime.now(), 300, false);
        MatchResult result = continuousMatcher.execute(new_order);
        assertThat(result.remainder().getQuantity()).isEqualTo(200);
        assertThat(orderBook.getSellQueue().peek().getOrderId()).isEqualTo(11);
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.SELL, 600,
                15700, 1, 1, 0, 300, 0);
        orderHandler.handleRequest(updateReq);
        assertThat(orderBook.getSellQueue().peek().getOrderId()).isEqualTo(11);
        assertThat(orderBook.findByOrderId(Side.SELL, 11).getQuantity()).isEqualTo(600);
    }

    @Test
    void update_order_request_with_same_minimum_and_priority_change_works() {
        Order new_order = new Order(11, security, Side.SELL,
                504, 15700, broker1, shareholder,
                LocalDateTime.now(), 300, false);
        MatchResult result = continuousMatcher.execute(new_order);
        assertThat(result.remainder().getQuantity()).isEqualTo(200);
        assertThat(orderBook.getSellQueue().peek().getOrderId()).isEqualTo(11);
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.SELL, 600,
                15900, 1, 1, 0, 300, 0);
        orderHandler.handleRequest(updateReq);
        assertThat(orderBook.findByOrderId(Side.SELL, 11).getQuantity()).isEqualTo(600);
    }

    @Test
    void no_minimum_execution_check_after_update_with_no_remainder() {
        Order new_order = new Order(11, security, Side.BUY,
                400, 15700, broker1, shareholder,
                LocalDateTime.now(), 300, true);
        orderBook.enqueue(new_order);

        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.BUY, 50,
                15800, 1, 1, 0, 300, 0);

        orderHandler.handleRequest(updateReq);
        assertThat(new_order.getQuantity()).isEqualTo(0);
        assertThat(orderBook.findByOrderId(Side.BUY, 11)).isEqualTo(null);
    }

    @Test
    void no_minimum_execution_check_after_update_with_remainder() {
        Order new_order = new Order(11, security, Side.BUY,
                400, 15700, broker1, shareholder,
                LocalDateTime.now(), 300, true);
        orderBook.enqueue(new_order);

        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.BUY, 450,
                15800, 1, 1, 0, 300, 0);

        orderHandler.handleRequest(updateReq);
        assertThat(new_order.getQuantity()).isEqualTo(100);
        assertThat(orderBook.findByOrderId(Side.BUY, 11)).isNotEqualTo(null);
    }

    @Test
    void update_order_request_with_different_minimum_fails() {
        Order new_order = new Order(11, security, Side.SELL,
                500, 15700, broker1, shareholder,
                LocalDateTime.now(), 300, false);
        orderBook.enqueue(new_order);
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.SELL, 600,
                15700, 1, 1, 0, 400, 0);
        orderHandler.handleRequest(updateReq);
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getErrors()).containsOnly(Message.CANNOT_MODIFY_MINIMUM_EXECUTION_QUANTITY);
    }
}






