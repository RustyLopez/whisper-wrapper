package com.chaostensor.whisperwrapper.repository;

import com.chaostensor.whisperwrapper.entity.WhisperJob;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface WhisperJobRepository extends ReactiveCrudRepository<WhisperJob, UUID> {

    Flux<WhisperJob> findAll();

    Mono<WhisperJob> findById(UUID id);

    Mono<WhisperJob> findByHash(String hash);
}