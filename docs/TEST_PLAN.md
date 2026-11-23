# Plano de Testes — UBA Vendor Portal (VMS)

Versão 1.0 · cobre a release com **Service Areas dinâmicas** e **gestão de perfis por departamento** (migração `V2`).

## 1. Objectivo e âmbito

Validar que os fluxos do portal funcionam de ponta a ponta e respeitam a segregação de funções definida no BRD (secção 7 — Stakeholders & Roles), antes de o Internal Audit dar a *finding* como encerrada.

**Dentro do âmbito**

| Módulo | O que se testa |
|---|---|
| Settings › Service Areas | criar, renomear, activar/desactivar, remover; efeito nos formulários e nos fluxos |
| Settings › Roles | departamentos, perfis Officer/Approver, regras dos departamentos de sistema |
| Settings › Users | atribuição de perfis, último administrador, desactivação |
| Controlo de acesso | o que cada perfil vê e pode fazer (UI **e** API) |
| Vendor Onboarding | registo, revisão, aprovação/rejeição, segregação de funções |
| Approval Flows | passos por perfil ou por pessoa, validações |
| Contracts & Approvals | submissão, aprovação sequencial, Return, Reject, Reassign, activação |
| Migração | BD existente (V1 com dados) → V2; instalação nova (V1+V2) |

**Fora do âmbito:** login por Active Directory, módulos "Coming soon", desempenho/carga, testes de penetração.

## 2. Modelo de perfis (referência para os testes)

Cada departamento tem um perfil **Approver**; Corporate Services e Legal têm também **Officer** (departamentos de sistema, não podem ser desactivados nem perder os Officers).

| Departamento (BRD) | Perfis | Código |
|---|---|---|
| Corporate Services (Head of Corporate Services) | Officer + Approver | `CS_OFFICER`, `CS_APPROVER` |
| Legal (Legal Officer) | Officer + Approver | `LEGAL_OFFICER`, `LEGAL_APPROVER` |
| Risk & Compliance (Head of Risk & Compliance) | Approver | `RISK_APPROVER` |
| Compliance – AML & Regulatory (Compliance Officer) | Approver | `COMPLIANCE_APPROVER` |
| CISO / IT | Approver | `IT_APPROVER` |
| Internal Audit | Approver | `AUDIT_APPROVER` |
| Chief Risk Officer | Approver | `CRO_APPROVER` |
| Managing Director | Approver | `MD_APPROVER` |
| — (sistema) | System Administrator | `SYS_ADMIN` |
| — (sistema) | UBA Staff (só leitura) | `UBA_STAFF` |

### Matriz de permissões esperada

| Acção | SYS_ADMIN | CS_OFFICER | CS_APPROVER | LEGAL_OFFICER | outros *_APPROVER | UBA_STAFF |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| Ver dashboard, vendors, contratos, aprovações | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Menu e páginas/APIs de **Settings** | ✔ | ✘ | ✘ | ✘ | ✘ | ✘ |
| Registar/editar vendor, contactos, documentos de vendor | ✔ | ✔ | ✘ | ✘ | ✘ | ✘ |
| Iniciar revisão, aprovar, rejeitar vendor | ✔ | ✘ | ✔ | ✘ | ✘ | ✘ |
| Criar/editar/submeter contrato, documentos de contrato | ✔ | ✔ | ✘ | ✔ | ✘ | ✘ |
| Decidir/reatribuir um passo de aprovação | ✔ (override) | só se o passo for do seu perfil ou atribuído a si | ← | ← | ← | ✘ |

Regras complementares:
- Um passo de fluxo é atribuído a um **perfil aprovador** (qualquer pessoa activa com o perfil decide) e, opcionalmente, a **uma pessoa** com esse perfil.
- Com *Segregation of duties* ligada: quem registou o vendor não o aprova/rejeita; quem submeteu o contrato não o aprova.
- Alterações de perfil ou de estado de um utilizador têm efeito **no pedido seguinte** (não é preciso novo login). Um utilizador desactivado perde a sessão.

## 3. Ambiente e dados de teste

- Ambiente de **teste/UAT** com SQL Server (nunca produção). Base de dados criada pelo Flyway.
- Utilizadores de teste (password à escolha, ex. `Test#2026`):

| Username | Perfil | Papel nos testes |
|---|---|---|
| `admin` | System Administrator | configuração, override |
| `cso` | Corporate Services Officer | regista vendors, cria contratos |
| `csa` | Corporate Services Approver | aprova vendors |
| `lgo` | Legal Officer | cria/submete contratos |
| `lga`, `lga2` | Legal Approver | passo de aprovação por perfil |
| `cmp` | Compliance (AML & Regulatory) Approver | passo atribuído a pessoa concreta |
| `mda` | Managing Director Approver | passo final, reatribuição |
| `view` | UBA Staff (read-only) | só leitura |

- Service Area de teste: **Procurement Services**, com fluxo próprio de 3 passos:
  1. *Legal Review* → perfil **Legal Approver** (qualquer pessoa)
  2. *Compliance Review* → perfil **Compliance Approver**, pessoa **cmp**
  3. *MD Approval* → perfil **Managing Director Approver** (qualquer pessoa)

## 4. Execução automática

O script `scripts/e2e/e2e_flows.py` executa todos os casos marcados **[A]** contra uma instância a correr, criando os seus próprios dados (com sufixo aleatório, pode repetir-se):

```bash
pip install requests
VMS_URL=http://localhost:8080 VMS_ADMIN_PASS=admin python3 scripts/e2e/e2e_flows.py
```

Sai com código 0 se tudo passar; lista os casos falhados pelo ID. Os casos **[M]** são manuais (UI/visuais) e devem ser feitos no browser.

> ⚠️ O script liga e desliga a *Segregation of duties* e deixa dados de teste. Usar só em ambiente de testes.

## 5. Casos de teste

Formato: **ID** · pré-condição / passos · resultado esperado · tipo (A = automático, M = manual).

### 5.1 Migração (fazer primeiro)

| ID | Passos | Resultado esperado | Tipo |
|---|---|---|---|
| TC-MG-01 | Fazer backup da BD actual (V1 com dados). Arrancar a nova versão. | Log: `Successfully applied 1 migration ... now at version v2`. Arranque sem erros. | M |
| TC-MG-02 | Logo após o arranque (sem reiniciar), criar um utilizador ou vendor. | Gravação com sucesso (não aparece *"A result set was generated for update"*). | M |
| TC-MG-03 | Settings › Users. | Utilizadores antigos mantêm o perfil: *Approver* → **Corporate Services Approver**, *Compliance Officer* → **Compliance (AML & Regulatory) Approver**, *Risk Team* → **Risk & Compliance Approver**, *Corporate Service Officer* → **Corporate Services Officer**. | M |
| TC-MG-04 | Settings › Roles. | 8 departamentos do BRD; CS e Legal com Officer + Approver; os restantes só Approver; System roles: Admin e UBA Staff. | M |
| TC-MG-05 | Settings › Approval Flows (fluxos que já existiam). | Passos mantêm o aprovador (pessoa) e ganham um perfil (o perfil aprovador dessa pessoa, ou System Administrator). | M |
| TC-MG-06 | Contrato que estava *In approval* antes da migração: o aprovador abre Approvals › My pending e aprova até ao fim. | Tarefas aparecem ao aprovador original; após o último passo o contrato fica **Active**. | M |
| TC-MG-07 | Instalação nova: BD vazia, arrancar. | Aplica V1 e V2; login admin/admin; script e2e passa. | M |

### 5.2 Service Areas

| ID | Passos | Resultado esperado | Tipo |
|---|---|---|---|
| TC-SA-01 | admin › Service Areas › New: "Procurement Services". | Área criada, *Active*, "Default flow". | A |
| TC-SA-02 | Criar outra com o mesmo nome. | Erro "Ja existe uma area de servico com este nome". | A |
| TC-SA-03 | Criar com nome vazio. | Erro de validação. | A |
| TC-SA-04 | Editar → renomear e alterar descrição. | Nome novo na lista; vendors/contratos existentes mostram o nome novo. | A |
| TC-SA-05 | Vendor Onboarding › Register: abrir o select de Service Area. | A área nova está disponível. | A |
| TC-SA-06 | Approval Flows. | A área nova aparece na lista de âmbitos. | A |
| TC-SA-07 | Criar área "Temp" e removê-la (Delete). | Removida. O botão Delete só aparece em áreas sem vendors, contratos e fluxo. | A |
| TC-SA-08 | Criar área inactiva; tentar registar vendor com ela (via API). | Erro "A area de servico ... esta inactiva". | A |
| TC-SA-09 | Formulários de vendor e contrato. | A área inactiva não aparece. | A |
| TC-SA-10 | Remover área com vendors/contratos ou fluxo próprio. | Erro 409 a pedir para desactivar (ou remover o fluxo). | A |
| TC-SA-11 | Desactivar área com fluxo; abrir Approval Flows. | A área sai da lista de âmbitos. | A |
| TC-SA-12 | Abrir vendor existente da área desactivada. | Continua a mostrar a área. | A |
| TC-SA-13 | Editar um vendor *Pending* dessa área. | O select mostra "<área> (inactive)" seleccionada (não troca de área sem querer). | A |
| TC-SA-14 | Gravar a edição mantendo a área inactiva. | Permitido. Trocar para outra área inactiva é recusado. | A |
| TC-SA-15 | Contrato de uma área desactivada que ainda tem fluxo próprio: submeter. | Usa o fluxo da área (o fluxo não é apagado ao desactivar). | M |
| TC-SA-16 | Registo de auditoria (BD `audit_log`, entity_type `SERVICE_AREA`). | CREATE/UPDATE/DELETE gravados com antes/depois. | M |

### 5.3 Roles (departamentos)

| ID | Passos | Resultado esperado | Tipo |
|---|---|---|---|
| TC-RO-01 | New department: código `PROCUREMENT`, sem "Has officers". | Criado com o perfil **Procurement Approver**. | A |
| TC-RO-02 | Ver Roles. | Só Approver (sem Officer). | A |
| TC-RO-03 | Criar outro com o mesmo código. | Erro 409. | A |
| TC-RO-04 | Código inválido (ex. `9x`, com espaços). | Erro de validação. | A |
| TC-RO-05 | Editar → ligar "Has officers". | Aparece o perfil **Procurement Officer**. | A |
| TC-RO-06 | Editar Corporate Services → desactivar. | Bloqueado (departamento de sistema). Na UI a checkbox está desactivada. | A |
| TC-RO-07 | Editar Legal → desligar Officers. | Bloqueado. | A |
| TC-RO-08 | Settings › Users › New user › Role. | Lista todos os perfis activos do BRD + System. | A |
| TC-RO-09 | Desactivar um departamento cujo perfil tem utilizadores activos. | Erro 409 com o número de utilizadores. | A |
| TC-RO-10 | Desactivar um departamento cujo perfil é usado num passo de fluxo ou tarefa em curso. | Erro 409 indicando os passos/tarefas. | M |
| TC-RO-11 | Desligar Officers num departamento (não sistema) com Officers activos. | Erro 409. Sem utilizadores → o perfil Officer fica inactivo e desaparece de Users. | M |
| TC-RO-12 | Renomear departamento. | Os nomes dos perfis acompanham (ex. "Procurement & Supply Approver"). | M |

### 5.4 Users

| ID | Passos | Resultado esperado | Tipo |
|---|---|---|---|
| TC-US-01 | Criar os utilizadores da secção 3. | Criados; tabela mostra Department e Role. | A |
| TC-US-02 | Criar utilizador sem perfil. | Erro "Seleccione o perfil". | A |
| TC-US-03 | Login com cada utilizador. | Entra; o nome no topo mostra os perfis em *tooltip*. | A |
| TC-US-04 | Com `cso` com sessão aberta, admin muda-lhe o perfil para UBA Staff; `cso` tenta registar vendor. | Bloqueado de imediato (403), sem novo login. | A |
| TC-US-05 | Admin desactiva `view` com sessão aberta; `view` navega. | É enviado para o login com a mensagem "Your user has been deactivated". | A |
| TC-US-06 | Login com `view` desactivado. | Recusado. | A |
| TC-US-07 | Tirar o perfil de administrador ao único admin activo (ou desactivá-lo). | Erro "Tem de existir pelo menos um administrador do sistema activo". | A |
| TC-US-08 | Desactivar o próprio utilizador. | Erro. | M |
| TC-US-09 | Email/username duplicado. | Erro 409. | M |

### 5.5 Controlo de acesso

| ID | Passos | Resultado esperado | Tipo |
|---|---|---|---|
| TC-AC-01 | `view`: abrir Dashboard, Vendors, Contracts, Approvals. | Tudo visível. | A |
| TC-AC-02 | `view`: abrir `/settings/users` pelo URL. | Página de erro 403. | A |
| TC-AC-03 | `view`: ver menu e Vendors. | Sem grupo SETTINGS; sem "Register new vendor", "New contract", Edit, Approve, Upload. | A/M |
| TC-AC-04 | `view`: `POST /api/vendors`. | 403. | A |
| TC-AC-05 | `view`: `/contracts/new`. | 403. | A |
| TC-AC-06 | `cso`, `csa`, `lgo`, `lga`: Settings e `PUT /api/flows/0`. | 403. | A |
| TC-AC-07 | `csa`: registar vendor. | 403. Botão não aparece. | A |
| TC-AC-08 | `lgo`: registar vendor. | 403. | A |
| TC-AC-09 | `lgo` / `csa` / `lga`: carregar ou remover documento de vendor (`/api/documents`). | 403. `cso` consegue. | M |
| TC-AC-10 | `csa`: carregar documento de contrato. | 403. `cso` e `lgo` conseguem. | M |
| TC-AC-11 | Para cada perfil, percorrer o menu e as páginas de detalhe. | Só aparecem os botões que a matriz da secção 2 permite. | M |

### 5.6 Vendor Onboarding

| ID | Passos | Resultado esperado | Tipo |
|---|---|---|---|
| TC-VO-01 | `cso` regista vendor (contacto, documento opcional). | Criado em *Pending*, código `UBA-VND-…`. | A |
| TC-VO-02 | `cso` tenta aprovar. | 403; botões de revisão não aparecem. | A |
| TC-VO-03 | `lga` tenta aprovar. | 403. | A |
| TC-VO-04 | `csa`: Start review. | *Under review*. | A |
| TC-VO-05 | `csa`: Approve. | *Approved*; aparece "New contract" (para quem cria contratos). | A |
| TC-VO-06 | `csa`: Reject sem motivo. | Erro. | A |
| TC-VO-07 | `csa`: Reject com motivo. | *Rejected*; motivo visível no detalhe. | A |
| TC-VO-08 | Criar contrato para vendor rejeitado. | Erro; vendor não aparece no select. | A |
| TC-VO-09 | SoD ligada: admin regista vendor e tenta aprovar. | 403 "quem registou o vendor nao o pode aprovar". | A |
| TC-VO-10 | SoD ligada: `csa` aprova esse vendor. | Aprovado. | A |
| TC-VO-11 | NUIT com ≠ 9 dígitos / duplicado. | Erro de validação / 409. | M |
| TC-VO-12 | Editar vendor *Approved*. | Botão Edit não aparece; API recusa. | M |

### 5.7 Approval Flows

| ID | Passos | Resultado esperado | Tipo |
|---|---|---|---|
| TC-AF-01 | Abrir o fluxo de uma área; select "Approver role". | Só perfis Approver (+ System Administrator); nenhum Officer. | A |
| TC-AF-02 | Passo com perfil Legal Approver e pessoa `cmp` (sem esse perfil) via API. | Erro "... nao tem o perfil ...". Na UI, o select de pessoa só lista quem tem o perfil escolhido. | A/M |
| TC-AF-03 | Passo com perfil Officer (API). | Erro. | A |
| TC-AF-04 | Passo "Anyone with the role" num perfil sem utilizadores activos. | Erro "Nenhum utilizador activo tem o perfil ...". | A |
| TC-AF-05 | Passo sem perfil. | Erro. | A |
| TC-AF-06 | Gravar o fluxo de 3 passos da secção 3. | Gravado; após reload mostra perfil e pessoa correctos em cada passo. | A |
| TC-AF-07 | Lista de âmbitos. | A área mostra "3 step(s)". | A |
| TC-AF-08 | Reordenar (↑/↓) e remover passos; gravar. | Numeração 1..n refeita; ordem gravada. | M |
| TC-AF-09 | "Use default flow". | Fluxo próprio removido; a área volta a "default". Contratos já em aprovação não são afectados (snapshot). | M |
| TC-AF-10 | Área sem fluxo próprio: submeter contrato. | Usa o fluxo Default. | M |

### 5.8 Contratos e aprovações

| ID | Passos | Resultado esperado | Tipo |
|---|---|---|---|
| TC-CT-01 | `csa` cria contrato (API). | 403; botão "New contract" não aparece. | A |
| TC-CT-02 | `lgo` cria contrato para vendor aprovado, área Procurement. | *Draft*; número `CTR-AAAA-xxxxx`. | A |
| TC-CT-03 | `lgo` submete. | Pedido criado. | A |
| TC-CT-04 | Ver contrato. | *In approval*; separador Approval mostra os 3 passos (1 Pending, 2–3 Waiting). | A |
| TC-AP-01 | `lga` › Approvals › My pending. | Passo 1 aparece (atribuído ao perfil, aparece como "Any Legal Approver"). | A |
| TC-AP-02 | `lga2` › My pending. | O mesmo passo aparece. | A |
| TC-AP-03 | `cmp` › My pending. | Não aparece. Em *All pending* aparece sem botões. | A |
| TC-AP-04 | `cmp` decide o passo 1 (API). | 403. | A |
| TC-AP-05 | `lga` aprova com comentário. | Passo 1 *Approved*; passo 2 passa a *Pending*. | A |
| TC-AP-06 | Histórico do contrato. | Mostra "(decided by LGA …)" e o comentário. | A |
| TC-AP-07 | `lga2` › My pending. | O passo 1 desapareceu. Contador do menu actualizado. | A |
| TC-AP-08 | `cmp` › My pending. | Passo 2 aparece (atribuído a si). | A |
| TC-AP-09 | `lga` decide passo 2. | 403. | A |
| TC-AP-10 | `cmp` aprova. | Passo 3 *Pending*. | A |
| TC-AP-11 | `mda` › My pending. | Passo 3 aparece. | A |
| TC-AP-12 | `lga` reatribui o passo 3. | 403. | A |
| TC-AP-13 | `mda` reatribui sem motivo. | Erro. | A |
| TC-AP-14 | `mda` reatribui a `lga2` com motivo "MD de férias". | OK. | A |
| TC-AP-15 | Caixas de `mda` e `lga2`. | Sai da de `mda`, entra na de `lga2`. | A |
| TC-AP-16 | Histórico. | "Reassigned from role queue to LGA2 … : MD de férias". | A |
| TC-AP-17 | `lga2` aprova. | OK. | A |
| TC-AP-18 | Ver contrato. | **Active**, com data de activação. | A |
| TC-AP-19 | Decidir de novo a mesma tarefa. | Erro "ja nao esta pendente". | A |
| TC-AP-20 | SoD ligada: quem submeteu tenta aprovar um passo seu. | 403. | M |
| TC-AP-21 | Desactivar o utilizador `cmp` com um passo atribuído a ele; admin reatribui. | Reatribuição funciona; o novo aprovador decide. | M |
| TC-RR-01 | Novo contrato; `lga` faz Return sem comentário. | Erro. | A |
| TC-RR-02 | Return com comentário. | Contrato volta a *Draft*; passos seguintes *Cancelled*. | A |
| TC-RR-03 | `cso` edita e re-submete. | Novo pedido (#2) com os passos do fluxo actual. | A |
| TC-RR-04 | `lga` Reject com comentário. | Contrato *Rejected*; pode ser editado e re-submetido. | A |
| TC-DF-01 | Admin decide um passo que não é seu. | Permitido (override, fica registado como decidido pelo admin). | A |

### 5.9 Regressão geral (manual)

| ID | Verificação |
|---|---|
| TC-RG-01 | Dashboard: contadores e gráficos carregam para todos os perfis. |
| TC-RG-02 | Pesquisa e filtros de Vendors (nome, NUIT, estado, área) e Contracts (estado, *Expiring*). |
| TC-RG-03 | Upload e download de documentos (≤ 20 MB; > 20 MB dá erro). |
| TC-RG-04 | Settings › General: guardar SoD e dias de aviso. |
| TC-RG-05 | Logout e login; CSRF (pedidos sem token são recusados). |
| TC-RG-06 | `audit_log` regista as acções de cada caso (CREATE, UPDATE, REVIEW_*, SUBMIT, APPROVE, REASSIGN). |

## 6. Cenários UAT por stakeholder (BRD secção 7)

Para a sessão com os donos de negócio — cada um executa o seu cenário com o seu utilizador:

| Stakeholder | Cenário |
|---|---|
| Head of Corporate Services (BRD Owner) | Officer regista vendor → Head (CS Approver) aprova; cria/configura Service Areas com o admin. |
| Legal Officer | Cria contrato, carrega minuta, submete; Legal Approver aprova o passo *Legal Review*. |
| Compliance Officer | Recebe o passo *Compliance Review* atribuído a si e aprova/devolve com comentário. |
| Head of Risk & Compliance / CRO | Passo de aprovação por perfil; reatribuição a um colega durante ausência. |
| CISO / IT | Fluxo da área *Information Technology* inclui passo *IT Security Assessment* (perfil CISO / IT Approver). |
| Managing Director | Passo final de contratos de valor elevado (fluxo da área). |
| Internal Audit | Com perfil só de leitura (ou Internal Audit Approver), confirma que vê tudo, não altera nada, e que o histórico/auditoria evidencia quem fez o quê. |

## 7. Critérios de entrada e saída

**Entrada:** build compila; BD de teste com backup; utilizadores da secção 3 criados; script e2e disponível.

**Saída (go-live):**
- 100% dos casos **[A]** a passar (`e2e_flows.py` sai com 0);
- todos os casos de migração (5.1) e de controlo de acesso (5.5) a passar;
- nenhum defeito crítico/alto aberto; médios com plano aceite pelo Head of Corporate Services;
- cenários UAT da secção 6 assinados pelos stakeholders e evidência entregue ao Internal Audit.

## 8. Registo de defeitos

Para cada falha registar: ID do caso, utilizador/perfil, passos, resultado obtido vs esperado, captura de ecrã e, se API, a resposta JSON. Severidade: **Crítico** (bloqueia fluxo ou fura permissões), **Alto** (regra de negócio errada), **Médio** (contornável), **Baixo** (cosmético).
