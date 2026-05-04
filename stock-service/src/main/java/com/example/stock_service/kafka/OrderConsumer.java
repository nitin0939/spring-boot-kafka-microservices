package com.example.stock_service.kafka;

import com.example.base_domains.dto.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderConsumer {
    private static final Logger logger= LoggerFactory.getLogger(OrderConsumer.class);

    @KafkaListener
    (topics = "${spring.kafka.topic.name}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(OrderEvent event){
        logger.info(String.format("Order Event Received in stock service => %s", event.toString()));

        //save order to database
    }
}

