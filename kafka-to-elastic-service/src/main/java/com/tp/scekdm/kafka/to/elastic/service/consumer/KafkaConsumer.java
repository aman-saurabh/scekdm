package com.tp.scekdm.kafka.to.elastic.service.consumer;

import org.apache.avro.specific.SpecificRecordBase;

import java.io.Serializable;
import java.util.List;

public interface KafkaConsumer<K extends Serializable, V extends SpecificRecordBase>{
    void recieve(List<V> messages, List<Integer> keys, List<Integer> partitions, List<Long> offsets);
}
