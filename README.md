# UBA Vendor Portal (VMS) — primeira versão funcional

Spring Boot 3 + **JPA/Hibernate** + SQL Server + Thymeleaf + CSS/JS puro (sem React, sem Bootstrap, sem bibliotecas de gráficos).

**Inclui:** login · dashboard · Vendor Onboarding (registo com contactos e documentos, aprovar/rejeitar) ·
Contracts (sem adendas) · Approvals (caixa de entrada, aprovar/devolver/rejeitar, reassign) ·
Settings (General, Service Areas, Approval Flows por área de serviço, Roles por departamento, Users) · controlo de acesso por perfil · auditoria · armazenamento local de ficheiros.
Os restantes módulos aparecem desactivados no menu ("Coming soon").

> **Estado:** compila e arranca contra SQL Server 2022; migrações V1 → V2 testadas numa BD com dados e numa BD vazia.
> Os fluxos estão cobertos pelo plano de testes [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) e pelo teste automático
> `scripts/e2e/e2e_flows.py`.

## Requisitos
- JDK 17+ e Maven 3.9+
- SQL Server (local, container Docker ou o da empresa)

## Correr localmente
1. **SQL Server** (opção Docker): `docker compose up -d`
   (ou use o da empresa e ajuste as variáveis abaixo).
2. **Criar a base de dados vazia** (uma vez):
   ```
   docker exec -it vms-sqlserver /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "Uba_Vms_2026!" -Q "CREATE DATABASE vms"
   ```
   (se o caminho for `/opt/mssql-tools/bin/sqlcmd`, retire o `-C`).
3. **Arrancar**: `mvn spring-boot:run`
   O Flyway cria as tabelas e os dados iniciais (`V1__init.sql`) no primeiro arranque.
4. Abrir <http://localhost:8080> — login **admin / admin**.

Variáveis (opcionais): `VMS_DB_URL`, `VMS_DB_USER`, `VMS_DB_PASSWORD`, `VMS_STORAGE_DIR` (pasta dos ficheiros, por defeito `./storage`).

## Fluxo para experimentar
1. *Vendor Onboarding → Register new vendor* (preencha contactos e, se quiser, documentos).
2. Abra o vendor → **Approve** (ou *Start review* primeiro).
3. *Contracts → New contract* (só aparecem vendors aprovados) → **Submit for approval**.
4. *Approvals* → aprovar cada passo; após o último o contrato fica **Active**. Teste **Return**, **Reject** e **Reassign**.
5. *Settings → Service Areas*: crie/renomeie/desactive áreas. *Settings → Roles*: departamentos e perfis.
   *Settings → Users*: crie utilizadores e atribua o perfil. *Settings → Approval Flows*: defina o fluxo por área
   (cada passo vai para um perfil aprovador e, opcionalmente, para uma pessoa com esse perfil). Sem fluxo próprio, usa-se o *Default*.
6. Por defeito a segregação de funções está desligada (só existe o admin). Ligue em *Settings → General*.

## Perfis e permissões
Cada departamento (BRD secção 7) tem um perfil **Approver**; Corporate Services e Legal têm também **Officer**.
Novos departamentos criam-se em *Settings → Roles* (com ou sem Officers).

| Perfil | Pode |
|---|---|
| System Administrator | tudo, incluindo Settings; pode decidir qualquer passo (override) |
| Corporate Services Officer | registar/editar vendors e documentos; criar/submeter contratos |
| Corporate Services Approver | rever, aprovar e rejeitar vendors; decidir passos do seu perfil |
| Legal Officer | criar/editar/submeter contratos |
| Restantes Approvers (Legal, Risk & Compliance, Compliance, CISO/IT, Internal Audit, CRO, MD) | decidir os passos atribuídos ao seu perfil ou a si |
| UBA Staff | só leitura |

As permissões são lidas da BD em cada pedido (mudar o perfil de alguém tem efeito imediato; desactivar termina a sessão).
As regras estão em `common/CurrentUser.java` (serviços) e `config/AccessInterceptor.java` (Settings).

## Testes
```
pip install requests
VMS_URL=http://localhost:8080 VMS_ADMIN_PASS=admin python3 scripts/e2e/e2e_flows.py
```
Cria dados de teste com sufixo aleatório — **só em ambiente de testes**. Plano completo (manual + automático): `docs/TEST_PLAN.md`.

## Estrutura
```
src/main/java/com/ubagroup/vendorportal
  controller/                  XxxController: páginas Thymeleaf + endpoints JSON /api/... (um por módulo, como os Services)
  service/                     XxxService: regras de negócio (Vendor, Contract, Approval, Flow, User, Document, Audit, Dashboard, Settings)
  repository/                  XxxRepository (Spring Data JPA)
  entity/                      entidades JPA (uma por tabela)
  config/                      SecurityConfig (login; ponto onde entra o AD), WebConfig + LayoutInterceptor, GlobalExceptionHandler
  common/                      Jpa (consultas de leitura), CurrentUser, FileStorage (+LocalFileStorage), ApiException, Util
src/main/resources
  db/migration/V1__init.sql    esquema SQL Server (v4) + dados iniciais
  db/migration/V2__...sql      departamentos/perfis, service areas, passos por perfil
  templates/                   páginas Thymeleaf (fragments/shell.html = menu, topo, ícones)
  static/css/app.css           todo o estilo (cor UBA #DA291C)
  static/js/                   api.js (fetch + CSRF) e um .js por página
```

## Decisões
- **JPA/Hibernate**: escritas por entidades + repositórios; as listas/ecrãs usam JPQL `select new map(...)` (classe `Jpa`). O esquema é do Flyway (`ddl-auto: none`). O `audit_log` é escrito por JDBC (a tabela tem trigger e é append-only).
- **Regras nos serviços, não em triggers** (ex.: `ApprovalService.decide`). O contrato só fica ACTIVE
  depois de todos os passos aprovados.
- **Aprovações genéricas** (`entity_type` + `entity_id`): o módulo de Assets reutiliza o mesmo motor —
  basta um `case` em `ApprovalService.onClosed` e linhas em `entity_type`/`approval_flow`.
- **Ficheiros**: interface `FileStorage`; a BD guarda só a `storage_key`. Trocar por partilha de rede/blob = nova classe.
- **Login**: `{noop}admin` na BD só para desenvolvimento. Utilizadores criados em Settings usam BCrypt.
  Para AD: substituir o `UserDetailsService` por `ActiveDirectoryLdapAuthenticationProvider` (e tornar `password_hash` opcional).

## Notas de migração
- Não usar `SET NOCOUNT ON` em migrações: o Flyway usa uma ligação do pool que a aplicação reutiliza, e com NOCOUNT ON
  os INSERT do Hibernate falham com *"A result set was generated for update"* até reiniciar. A V2 repõe `NOCOUNT OFF`.

## Limitações conhecidas
- Sem adendas, Document Collection, DD, risco, auditoria na UI e relatórios (próximas fases).
- Auditoria já é gravada em `audit_log`, mas só o histórico recente do vendor é mostrado.
- Contratos só avançam de DRAFT/REJECTED; ainda não há acções para terminar/fechar (TERMINATED/CLOSED).
