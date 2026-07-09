package com.mysaas.tenant;

import java.util.List;
import java.util.Optional;

/**
 * Registry des tenants — permet de rechercher un tenant par slug.
 *
 * <p>Implémentations :
 *
 * <ul>
 *   <li>{@link InMemoryTenantRegistry} — pour les tests et le dev
 *   <li>{@link JdbcTenantRegistry} — pour la prod (table public.tenants)
 * </ul>
 */
public interface TenantRegistry {

  /**
   * Recherche un tenant par slug.
   *
   * @param slug le slug du tenant (ex: "acme")
   * @return le TenantContext si le tenant existe, null sinon
   */
  TenantContext find(String slug);

  /**
   * Liste tous les tenants enregistrés.
   *
   * @return la liste des tenants
   */
  List<TenantContext> findAll();

  /**
   * Crée un nouveau tenant.
   *
   * @param slug le slug du tenant
   * @param displayName le nom d'affichage
   * @return le TenantContext créé
   */
  TenantContext create(String slug, String displayName);

  /**
   * Supprime un tenant par slug.
   *
   * @param slug le slug du tenant
   * @return true si le tenant a été supprimé
   */
  boolean delete(String slug);

  /**
   * Vérifie si un tenant existe.
   *
   * @param slug le slug du tenant
   * @return true si le tenant existe
   */
  default boolean exists(String slug) {
    return find(slug) != null;
  }

  /**
   * Recherche optionnelle par slug.
   *
   * @param slug le slug du tenant
   * @return un Optional contenant le TenantContext si le tenant existe
   */
  default Optional<TenantContext> findOptional(String slug) {
    return Optional.ofNullable(find(slug));
  }
}
