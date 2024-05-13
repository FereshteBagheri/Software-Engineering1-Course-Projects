package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.ContinuousMatcher;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.OrderActivatedEvent;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class ContinuousMatcherTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    private StopOrderBook stopOrderBook;
    private List<StopLimitOrder> stopOrders;
    @Autowired
    EventPublisher eventPublisher;
    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;

    @Autowired
    private ContinuousMatcher continuousMatcher;

    @BeforeEach
    void setupOrderBook() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").lastTradePrice(15000).build();
        broker = Broker.builder().credit(100_000_000L).brokerId(1).build();
        shareholder = Shareholder.builder().shareholderId(1).build();
        shareholder.incPosition(security, 100_000);

        securityRepository.addSecurity(security);
        shareholderRepository.addShareholder(shareholder);
        brokerRepository.addBroker(broker);

        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, broker, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker, shareholder),
                new Order(7, security, Side.SELL, 285, 15810, broker, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    private void setUpStopOrderBook(){
        stopOrderBook = security.getStopOrderBook();
        stopOrders = Arrays.asList(
                new StopLimitOrder(11, security, Side.BUY, 300, 15800, broker, shareholder, 16300, 10),
                new StopLimitOrder(12, security, Side.BUY, 43, 15500, broker, shareholder, 16350, 11),
                new StopLimitOrder(13, security, Side.BUY, 445, 15450, broker, shareholder, 16400, 12),
                new StopLimitOrder(14, security, Side.BUY, 526, 15450, broker, shareholder, 16500, 13),
                new StopLimitOrder(15, security, Side.BUY, 1000, 15400, broker, shareholder, 16500, 14),
                new StopLimitOrder(16, security, Side.SELL, 350, 15800, broker, shareholder, 14600, 15),
                new StopLimitOrder(17, security, Side.SELL, 285, 15810, broker, shareholder, 14550, 16),
                new StopLimitOrder(18, security, Side.SELL, 800, 15810, broker, shareholder, 14500, 17),
                new StopLimitOrder(19, security, Side.SELL, 340, 15820, broker, shareholder, 14450, 18),
                new StopLimitOrder(20, security, Side.SELL, 65, 15820, broker, shareholder, 14400, 19)
        );
        stopOrders.forEach(stopOrder -> stopOrderBook.enqueue(stopOrder));
    }

    @Test
    void new_sell_order_matches_completely_with_part_of_the_first_buy() {
        Order order = new Order(11, security, Side.SELL, 100, 15600, broker, shareholder);
        Trade trade = new Trade(security, 15700, 100, orders.get(0), order);
        MatchResult result = continuousMatcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(204);
    }

    @Test
    void new_sell_order_matches_partially_with_the_first_buy() {
        Order order = new Order(11, security, Side.SELL, 500, 15600, broker, shareholder);
        Trade trade = new Trade(security, 15700, 304, orders.get(0), order);
        MatchResult result = continuousMatcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(196);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getOrderId()).isEqualTo(2);
    }

    @Test
    void new_sell_order_matches_partially_with_two_buys() {
        Order order = new Order(11, security, Side.SELL, 500, 15500, broker, shareholder);
        Trade trade1 = new Trade(security, 15700, 304, orders.get(0), order);
        Trade trade2 = new Trade(security, 15500, 43, orders.get(1), order.snapshotWithQuantity(196));
        MatchResult result = continuousMatcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(153);
        assertThat(result.trades()).containsExactly(trade1, trade2);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getOrderId()).isEqualTo(3);
    }

    @Test
    void new_buy_order_matches_partially_with_the_entire_sell_queue() {
        Order order = new Order(11, security, Side.BUY, 2000, 15820, broker, shareholder);
        List<Trade> trades = new ArrayList<>();
        int totalTraded = 0;
        for (Order o : orders.subList(5, 10)) {
            trades.add(new Trade(security, o.getPrice(), o.getQuantity(),
                    order.snapshotWithQuantity(order.getQuantity() - totalTraded), o));
            totalTraded += o.getQuantity();
        }

        MatchResult result = continuousMatcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(160);
        assertThat(result.trades()).isEqualTo(trades);
        assertThat(security.getOrderBook().getSellQueue()).isEmpty();
    }

    @Test
    void new_buy_order_does_not_match() {
        Order order = new Order(11, security, Side.BUY, 2000, 15500, broker, shareholder);
        MatchResult result = continuousMatcher.match(order);
        assertThat(result.remainder()).isEqualTo(order);
        assertThat(result.trades()).isEmpty();
    }

    @Test
    void iceberg_order_in_queue_matched_completely_after_three_rounds() {
        security = Security.builder().build();
        broker = Broker.builder().build();
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new IcebergOrder(1, security, Side.BUY, 450, 15450, broker, shareholder, 200),
                new Order(2, security, Side.BUY, 70, 15450, broker, shareholder),
                new Order(3, security, Side.BUY, 1000, 15400, broker, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Order order = new Order(4, security, Side.SELL, 600, 15450, broker, shareholder);
        List<Trade> trades = List.of(
                new Trade(security, 15450, 200, orders.get(0).snapshotWithQuantity(200), order.snapshotWithQuantity(600)),
                new Trade(security, 15450, 70, orders.get(1).snapshotWithQuantity(70), order.snapshotWithQuantity(400)),
                new Trade(security, 15450, 200, orders.get(0).snapshotWithQuantity(200), order.snapshotWithQuantity(330)),
                new Trade(security, 15450, 50, orders.get(0).snapshotWithQuantity(50), order.snapshotWithQuantity(130))
        );

        MatchResult result = continuousMatcher.match(order);

        assertThat(result.remainder().getQuantity()).isEqualTo(80);
        assertThat(result.trades()).isEqualTo(trades);
    }

    @Test
    void insert_iceberg_and_match_until_quantity_is_less_than_peak_size() {
        security = Security.builder().isin("TEST").build();
        shareholder.incPosition(security, 1_000);
        security.getOrderBook().enqueue(
                new Order(1, security, Side.SELL, 100, 10, broker, shareholder)
        );

        Order order = new IcebergOrder(1, security, Side.BUY, 120 , 10, broker, shareholder, 40 );
        MatchResult result = continuousMatcher.execute(order);

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.trades()).hasSize(1);
        assertThat(security.getOrderBook().getSellQueue()).hasSize(0);
        assertThat(security.getOrderBook().getBuyQueue()).hasSize(1);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getQuantity()).isEqualTo(20);

    }

    @Test
    void execute_triggered_stop_orders_works_when_increasing_last_price(){
        setUpStopOrderBook();
        long requestId = 1;
        continuousMatcher.executeTriggeredStopLimitOrders(security, eventPublisher, 16350, requestId);
        verify(eventPublisher).publish(new OrderActivatedEvent(10,11));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 11)).isEqualTo(null);
        verify(eventPublisher).publish(new OrderActivatedEvent(11,12));
        assertThat(stopOrderBook.findByOrderId(Side.BUY, 12)).isEqualTo(null);
    }

    @Test
    void execute_triggered_stop_orders_works_when_decreasing_last_price(){
        setUpStopOrderBook();
        long requestId = 1;
        continuousMatcher.executeTriggeredStopLimitOrders(security, eventPublisher, 14550, requestId);
        verify(eventPublisher).publish(new OrderActivatedEvent(15,16));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 16)).isEqualTo(null);
        verify(eventPublisher).publish(new OrderActivatedEvent(16,17));
        assertThat(stopOrderBook.findByOrderId(Side.SELL, 17)).isEqualTo(null);
    }

    @Test
    void new_stop_order_is_not_activated() {
        StopLimitOrder newOrder = new StopLimitOrder(15, security, Side.BUY, 1000, 15400, broker, shareholder, 16500, 20);
        MatchResult result = continuousMatcher.execute(newOrder);
        assertThat(result).isEqualTo(MatchResult.notActivated(newOrder));
        assertThat(security.getStopOrderBook().findByOrderId(Side.BUY, 15)).isNotEqualTo(null);
        assertThat(security.getOrderBook().findByOrderId(Side.BUY, 15)).isEqualTo(null);
    }

    @Test
    void new_stop_order_is_activated_and_not_matched(){
        StopLimitOrder newOrder = new StopLimitOrder(15, security, Side.BUY, 1000, 15400, broker, shareholder, 15000, 20);
        MatchResult result = continuousMatcher.execute(newOrder);
        assertThat(result.remainder().getQuantity()).isEqualTo(1000);
        assertThat(result.trades().size()).isEqualTo(0);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(security.getStopOrderBook().findByOrderId(Side.BUY, 15)).isEqualTo(null);
        assertThat(security.getOrderBook().findByOrderId(Side.BUY, 15)).isNotEqualTo(null);
    }

    @Test
    void new_stop_order_is_activated_and_partially_matched(){
        StopLimitOrder newOrder = new StopLimitOrder(15, security, Side.BUY, 400, 15800, broker, shareholder, 15000, 20);
        MatchResult result = continuousMatcher.execute(newOrder);
        assertThat(result.remainder().getQuantity()).isEqualTo(50);
        assertThat(result.trades().size()).isEqualTo(1);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(security.getOrderBook().findByOrderId(Side.BUY, 15)).isNotEqualTo(null);
    }

    @Test
    void new_stop_order_is_activated_and_completely_matched(){
        StopLimitOrder newOrder = new StopLimitOrder(15, security, Side.BUY, 350, 15800, broker, shareholder, 15000, 20);
        MatchResult result = continuousMatcher.execute(newOrder);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
        assertThat(result.trades().size()).isEqualTo(1);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(security.getOrderBook().findByOrderId(Side.BUY, 15)).isEqualTo(null);
    }
}
