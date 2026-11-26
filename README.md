# UBA Vendor Portal (VMS) — primeira versão funcional

Spring Boot 3 + **JPA/Hibernate** + SQL Server + Thymeleaf + CSS/JS puro (sem React, sem Bootstrap, sem bibliotecas de gráficos).

**Inclui:** login · dashboard · Vendor Onboarding (registo com contactos e documentos, aprovar/rejeitar) ·
Contracts (sem adendas) · Approvals (caixa de entrada, aprovar/devolver/rejeitar, reassign) ·
Settings (General, Approval Flows por área de serviço, Users) · auditoria · armazenamento local de ficheiros.
Os restantes módulos aparecem desactivados no menu ("Coming soon").

> ⚠️ **Estado:** código escrito sem acesso ao Maven Central, por isso **não foi compilado nem executado**
> (só verificada a sintaxe Java/JS e o equilíbrio das tags HTML). Espere pequenos erros no primeiro arranque
> e envie-me a mensagem de erro para corrigir.

## Requisitos
- JDK 17+ e Maven 3.9+
- SQL Server (local, container Docker ou o da empresa)

## Correr localmente
1. **SQL Server** instalado e com TCP/IP activo na porta 1433 (ou `docker compose up -d` para um de teste).
2. **Criar a base de dados vazia** (uma vez): `CREATE DATABASE vms;` (no SSMS).
3. **Ligação** (`application.yml`): por defeito usa **autenticação integrada do Windows**:
   `jdbc:sqlserver://localhost:1433;databaseName=vms;encrypt=true;trustServerCertificate=true;integratedSecurity=true`
   - O Windows user que corre a aplicação precisa de permissão `db_owner` na base `vms`.
   - O driver precisa da **`mssql-jdbc_auth-<versão>.x64.dll`** (vem no zip do "Microsoft JDBC Driver for SQL Server",
     pasta `auth/x64`). Copie-a para uma pasta do `PATH` do Windows, ou para `<JAVA_HOME>\bin`, ou arranque com
     `-Djava.library.path=C:\caminho\da\pasta`. Sem ela aparece `no mssql-jdbc_auth ... in java.library.path`.
   - Para usar login SQL (`sa`) em vez disso: retire `;integratedSecurity=true` do URL e defina `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`.
4. **Arrancar**: `mvn spring-boot:run` (o Flyway cria as tabelas e os dados iniciais no primeiro arranque).
5. Abrir <http://localhost:8080> — login **admin / admin**.

Variáveis (opcionais): `VMS_DB_URL`, `VMS_STORAGE_DIR` (pasta dos ficheiros, por defeito `./storage`).

## Perfis e permissões
| Perfil (`app_role`) | O que pode fazer |
|---|---|
| `SYS_ADMIN` | Tudo (é o `admin` inicial). |
| `CS_OFFICER` (Corporate Services) | **Só regista vendors**: criar/editar vendor, contactos e documentos do vendor. Não cria contratos nem aprova vendors. |
| `LEGAL` | **Só regista contratos**: criar/editar/submeter contrato e adicionar documentos do contrato. Não regista vendors. |
| `APPROVER`, `COMPLIANCE`, `RISK_TEAM` | Revêem (Start review / Approve / Reject) os vendors; decidem os passos de aprovação que lhes forem atribuídos. |
| `UBA_STAFF` | Só consulta. |

Todos os perfis vêem vendors, contratos, documentos e o percurso de aprovação. Os botões que o perfil não pode usar não aparecem
e o servidor também recusa (403). As regras estão em `SecurityConfig` (URLs), `DocumentController` (documentos por tipo de entidade)
e `CurrentUser` (`canManageVendors`, `canManageContracts`, `canReviewVendors`). O perfil `LEGAL` e a coluna `document.stage`
vêm da migração `V2__roles_document_stage.sql` (corre sozinha no arranque).

## Página do vendor
- **Overview**: dados da empresa, contactos, documentos da empresa, contratos e actividade recente.
- **Documents**: *todos* os documentos do vendor, os da empresa e os dos seus contratos, com quem os submeteu, quando, em que fase
  (estado do vendor/contrato no momento do upload) e os removidos assinalados.
- **Approval flow**: o percurso completo — registo, revisão do vendor, fluxo de aprovação de contratos da área de serviço
  (passos e aprovadores) e, por contrato, cada pedido com a decisão, data, comentários e reatribuições. Contratos ainda
  não submetidos mostram os passos que vão ser aplicados.

## Fluxo para experimentar
1. *Settings → Users*: crie um utilizador **CS_OFFICER**, um **LEGAL** e um **APPROVER** (ou use o `admin`, que pode tudo).
2. Como CS: *Vendor Onboarding → Register new vendor* (contactos e, se quiser, documentos).
3. Como APPROVER (ou admin): abra o vendor → **Approve** (ou *Start review* primeiro).
4. Como Legal: *Contracts → New contract* (só aparecem vendors aprovados), junte documentos → **Submit for approval**.
5. *Approvals* → aprovar cada passo; após o último o contrato fica **Active**. Teste **Return**, **Reject** e **Reassign**.
6. Na página do vendor veja os separadores **Documents** e **Approval flow**.
7. *Settings → Approval Flows*: defina o fluxo por área de serviço (um aprovador por passo). Sem fluxo próprio, usa-se o *Default*.
8. Por defeito a segregação de funções está desligada. Ligue em *Settings → General*.

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
  db/migration/V2__...sql      perfil LEGAL e coluna document.stage
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

## Limitações conhecidas
- Sem adendas, Document Collection, DD, risco, auditoria na UI e relatórios (próximas fases).
- Auditoria já é gravada em `audit_log`, mas só o histórico recente do vendor é mostrado.
- A revisão do vendor (onboarding) é um único passo; só os contratos usam o fluxo de vários passos configurável.
- Os documentos já existentes antes da migração V2 aparecem sem fase (`stage` vazio).
- Contratos só avançam de DRAFT/REJECTED; ainda não há acções para terminar/fechar (TERMINATED/CLOSED).
