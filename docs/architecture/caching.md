# Caching

> **Snapshot notice.** This file describes design ideas and may not match the implemented
> system. For code-verified documentation see [../architecture.md](../architecture.md) and
> [../microservices.md](../microservices.md).


## Strategy
- Cache read-heavy reference data
- Use Redis or in-memory caches where appropriate
- Avoid caching mutable medical records without invalidation strategy
