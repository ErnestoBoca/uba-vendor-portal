package com.ubagroup.vendorportal.common;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.stereotype.Component;

/**
 * Atalho para consultas JPQL de leitura (listas/ecras). Usa "select new map(...)" para devolver
 * Map<String,Object> prontos para as paginas Thymeleaf, sem criar uma classe DTO por ecra.
 * Escritas passam sempre pelas entidades + repositorios.
 */
@Component
public class Jpa {

    @PersistenceContext
    private EntityManager em;

    private <T> TypedQuery<T> query(String jpql, Class<T> type, Map<String, Object> params) {
        TypedQuery<T> q = em.createQuery(jpql, type);
        params.forEach(q::setParameter);
        return q;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Map<String, Object>> list(String jpql, Map<String, Object> params) {
        return (List) query(jpql, Map.class, params).getResultList();
    }

    public List<Map<String, Object>> list(String jpql) {
        return list(jpql, Map.of());
    }

    /** Lista paginada (first = indice do primeiro registo, 0-based). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Map<String, Object>> page(String jpql, Map<String, Object> params, int first, int max) {
        TypedQuery<Map> q = query(jpql, Map.class, params);
        q.setFirstResult(first);
        q.setMaxResults(max);
        return (List) q.getResultList();
    }

    public List<Map<String, Object>> top(String jpql, Map<String, Object> params, int max) {
        return page(jpql, params, 0, max);
    }

    public Map<String, Object> one(String jpql, Map<String, Object> params) {
        List<Map<String, Object>> rows = top(jpql, params, 1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long count(String jpql, Map<String, Object> params) {
        Long n = query(jpql, Long.class, params).getSingleResult();
        return n == null ? 0L : n;
    }

    /** Consultas que devolvem colunas simples (ex.: status, count). */
    public List<Object[]> rows(String jpql, Map<String, Object> params) {
        return query(jpql, Object[].class, params).getResultList();
    }
}
