package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.ChangeMatchingStateHandler;
import ir.ramtung.tinyme.domain.service.ContinuousMatcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
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
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class ChangeMatchingStateHandlerTest {
    @Autowired
    OrderHandler orderHandler;
    
    @Autowired
    private AuctionMatcher auctionMatcher;

    @Autowired
    ChangeMatchingStateHandler stateHandler;

    @Autowired
    EventPublisher eventPublisher;
    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;
    private Security security;
    private OrderBook orderBook;
    private Shareholder shareholder;
    private Broker broker1;
    private Broker broker2;
    private Broker broker3;

    @BeforeEach
    void setup() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);

        orderBook = security.getOrderBook();

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        broker1 = Broker.builder().brokerId(1).build();
        broker2 = Broker.builder().brokerId(2).build();
        broker3 = Broker.builder().brokerId(2).build();
        brokerRepository.addBroker(broker1);
        brokerRepository.addBroker(broker2);
        brokerRepository.addBroker(broker3);
    }

    @Test
    void trade_events_after_changing_state(){
        security.setMatchingState(MatchingState.AUCTION);
        List<Order> orders = Arrays.asList(
            new Order(1, security, Side.BUY, 450, 15900, broker1, shareholder),
            new Order(2, security, Side.SELL, 350, 15800, broker1, shareholder),
            new Order(3, security, Side.SELL, 285, 15810, broker1, shareholder),
            new Order(4, security, Side.SELL, 800, 15810, broker1, shareholder),
            new Order(5, security, Side.SELL, 340, 15820, broker1, shareholder),
            new Order(6, security, Side.SELL, 65, 15820, broker1, shareholder)            
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setLastTradePrice(15750);
        CustomPair pair = security.findOpeningPrice();
        int openingPrice = pair.getFirst();
        
        ChangeMatchingStateRq changeMatchingStateRq = new ChangeMatchingStateRq("ABC", MatchingState.CONTINUOUS);
        stateHandler.handleChangeMatchingStateRq(changeMatchingStateRq);

        
        verify(eventPublisher).publish(new TradeEvent(security.getIsin(), openingPrice, 350, 1, 2));
        verify(eventPublisher).publish(new TradeEvent(security.getIsin(), openingPrice, 100, 1, 3));
    }

    @Test
    void no_transaction_when_tradeable_quantity_equal_to_zero_in_auction_matching(){
        security.setMatchingState(MatchingState.AUCTION);
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, broker1, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker2, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker3, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker1, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker2, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker3, shareholder),
                new Order(7, security, Side.SELL, 285, 15810, broker1, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker2, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker3, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, broker1, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        LinkedList<Order> validBuyQueue = orderBook.getBuyQueue();
        LinkedList<Order> validSellQueue = orderBook.getSellQueue();
        ChangeMatchingStateRq changeMatchingStateRq = new ChangeMatchingStateRq("ABC", MatchingState.CONTINUOUS);
        stateHandler.handleChangeMatchingStateRq(changeMatchingStateRq);
        assertThat(orderBook.getBuyQueue()).isEqualTo(validBuyQueue);
        assertThat(orderBook.getSellQueue()).isEqualTo(validSellQueue);
        verify(eventPublisher, times(0)).publish(any(TradeEvent.class));
    }

    @Test
    void active_stop_limit_orders_after_changing_state(){
        security.setMatchingState(MatchingState.AUCTION);
        broker1.increaseCreditBy(100000000);
        broker2.increaseCreditBy(100000000);
        
        System.out.println("broker1 old credit: " + broker1.getCredit());
        System.out.println("broker2 old credit: " + broker2.getCredit());

        List<Order> orders = Arrays.asList(
            new Order(1, security, Side.BUY, 445, 17000, broker1, shareholder),
            new Order(2, security, Side.BUY, 304, 15700, broker1, shareholder),
            new Order(3, security, Side.BUY, 43, 15500, broker2, shareholder),
            new Order(4, security, Side.BUY, 445, 15450, broker1, shareholder),
            new Order(5, security, Side.BUY, 526, 15450, broker1, shareholder),
            new Order(6, security, Side.BUY, 1000, 15400, broker2, shareholder),
            new Order(7, security, Side.SELL, 560, 16330, broker2, shareholder),
            new Order(8, security, Side.SELL, 350, 16800, broker1, shareholder),
            new Order(9, security, Side.SELL, 285, 16810, broker1, shareholder),
            new Order(10, security, Side.SELL, 800, 16810, broker2, shareholder),
            new Order(11, security, Side.SELL, 340, 16820, broker2, shareholder),
            new Order(12, security, Side.SELL, 65, 16820, broker1, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        List<StopLimitOrder> stopOrders = Arrays.asList(
            new StopLimitOrder(13, security, Side.BUY, 430, 15500, broker2, shareholder, 16300, 11),
            new StopLimitOrder(14, security, Side.BUY, 1000, 15400, broker2, shareholder, 16500, 14),
            new StopLimitOrder(15, security, Side.SELL, 340, 15820, broker1, shareholder, 14450, 18),
            new StopLimitOrder(16, security, Side.SELL, 65, 15820, broker1, shareholder, 14400, 19)
        );
        stopOrders.forEach(stopOrder -> security.getStopOrderBook().enqueue(stopOrder));

        ChangeMatchingStateRq changeMatchingStateRq = new ChangeMatchingStateRq("ABC", MatchingState.CONTINUOUS);
        stateHandler.handleChangeMatchingStateRq(changeMatchingStateRq);

        // order1 and oder7 matching in auction mode, now new lastTradePrice is equal to openingPrice which is 16330
        // It makes order13 in stopLimitOrders activate, so it will be matched to order5 and exchange only 115 unit quantity
        
        assertThat(security.getOrderBook().findByOrderId(Side.BUY, 1)).isEqualTo(null);
        assertThat(security.getOrderBook().findByOrderId(Side.SELL, 7)).isEqualTo(null);
       assertThat(security.getStopOrderBook().findByOrderId(Side.BUY, 13)).isNotEqualTo(null);
       assertThat(security.getStopOrderBook().findByOrderId(Side.BUY, 13).getQuantity()).isEqualTo(315);

        // Check remaining credit
        System.out.println("broker1 new credit: " + broker1.getCredit());
        System.out.println("broker2 new credit: " + broker2.getCredit());
        // Check OrderActivatedEvent
    }

}