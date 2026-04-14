**Distributed Rate Limiter (Production-Grade Microservice)**

A high-performance distributed rate limiting service built using Java 21, Spring Boot, Redis, Docker, and Kubernetes.
Designed to handle high-concurrency workloads with sub-millisecond latency and support horizontal scaling.

**Overview**

This project implements a distributed rate limiting system using:
  1.Token Bucket Algorithm (burst-friendly)
  2.Sliding Window Algorithm (strict rate control)

All rate limit state is stored in Redis, ensuring consistency across multiple instances.
Atomicity is guaranteed using Lua scripts, preventing race conditions in distributed environments

**Features**

  Handles 10,000+ requests/sec
  Sub-millisecond latency using Redis
  Supports multiple algorithms (Strategy Pattern)
  Thread-safe and concurrency-friendly
  Atomic operations using Redis Lua scripts
  Clean REST APIs with proper HTTP status codes

**Architecture**

  Client
    ↓
  Controller (REST API)
    ↓
  Service Layer (Strategy Pattern)
    ↓
  Rate Limiting Algorithm
    ↓
  Redis (Lua Script - Atomic Execution)
    ↓
  Response (200 / 429)
