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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
} 