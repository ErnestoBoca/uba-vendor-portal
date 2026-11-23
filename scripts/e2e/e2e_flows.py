#!/usr/bin/env python3
"""
Teste end-to-end dos fluxos do VMS contra uma instancia a correr (ver docs/TEST_PLAN.md).

Uso:   pip install requests
       VMS_URL=http://localhost:8080 VMS_ADMIN_PASS=admin python3 scripts/e2e/e2e_flows.py

Cria dados novos com um sufixo aleatorio (pode ser corrido varias vezes na mesma BD).
NAO correr em producao: cria utilizadores, vendors, contratos e altera o setting de segregacao de funcoes.
Cada verificacao mostra o id do caso de teste (TC-xx) do plano.
"""
import html as htmllib
import json
import os
import random
import re
import sys

import requests

BASE = os.environ.get("VMS_URL", "http://localhost:8080").rstrip("/")
ADMIN_USER = os.environ.get("VMS_ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("VMS_ADMIN_PASS", "admin")
SFX = "%04d" % random.randint(0, 9999)
PASS = "Test#2026"

results = []


def check(tc, desc, cond, detail=""):
    results.append((tc, desc, bool(cond)))
    print(("  PASS " if cond else "  FAIL ") + tc + " " + desc + ("" if cond else "  -> " + str(detail)[:300]))


class Session:
    def __init__(self, user, pw):
        self.user = user
        self.s = requests.Session()
        r = self.s.get(BASE + "/login")
        tok = re.search(r'name="_csrf" value="([^"]+)"', r.text).group(1)
        r = self.s.post(BASE + "/login", data={"username": user, "password": pw, "_csrf": tok})
        self.ok = "/login" not in r.url
        if self.ok:
            self.tok = re.search(r'name="_csrf" content="([^"]+)"', self.s.get(BASE + "/").text).group(1)

    def h(self):
        return {"X-CSRF-TOKEN": self.tok}

    def get(self, p, **kw):
        return self.s.get(BASE + p, allow_redirects=False, **kw)

    def post(self, p, body=None):
        return self.s.post(BASE + p, json={} if body is None else body, headers=self.h(), allow_redirects=False)

    def put(self, p, body):
        return self.s.put(BASE + p, json=body, headers=self.h(), allow_redirects=False)

    def delete(self, p):
        return self.s.delete(BASE + p, headers=self.h(), allow_redirects=False)

    def vendor(self, name, area_id, nuit=None):
        data = {"supplierName": name, "email": "vendor@test.co.mz", "mobile": "+258 840000000", "address": "Av. 24 de Julho, Maputo",
                "nuit": nuit or str(random.randint(100000000, 999999999)), "serviceCategoryId": area_id,
                "contacts": [{"fullName": "Contact Person", "phone": "+258 840000001", "jobTitle": "Director"}], "documents": []}
        return self.s.post(BASE + "/api/vendors", files={"data": (None, json.dumps(data))}, headers=self.h())

    def contract(self, vendor_id, area_id, title):
        return self.post("/api/contracts", {"vendorId": vendor_id, "serviceCategoryId": area_id, "title": title,
                                            "contractType": "SERVICE", "startDate": "2026-12-01", "endDate": "2027-11-30",
                                            "valueAmount": 150000, "currency": "MZN"})


def ids_from_select(html, select_id):
    block = re.search(r'<select id="' + select_id + r'">(.*?)</select>', html, re.S).group(1)
    return {htmllib.unescape(name.strip()): int(i) for i, name in re.findall(r'<option value="(\d+)">([^<]+)</option>', block)}


def pending_tasks(sess, contract_id):
    """Tarefas PENDING do contrato que aparecem na caixa 'mine' do utilizador."""
    html = sess.get("/approvals?tab=mine").text
    return [int(t) for t in re.findall(r'href="/contracts/' + str(contract_id) + r'".*?data-decide="APPROVE" data-task="(\d+)"', html, re.S)]


def all_pending_task(admin, contract_id):
    html = admin.get("/approvals?tab=all").text
    m = re.search(r'href="/contracts/' + str(contract_id) + r'".*?data-task="(\d+)"', html, re.S)
    return int(m.group(1)) if m else None


def contract_status(sess, cid):
    html = sess.get("/contracts/%d" % cid).text
    m = re.search(r'class="badge b-([A-Z_]+)"', html)
    return m.group(1) if m else None


def main():
    print("VMS e2e  base=%s  suffix=%s" % (BASE, SFX))
    admin = Session(ADMIN_USER, ADMIN_PASS)
    if not admin.ok:
        sys.exit("Login do admin falhou.")
    admin.put("/api/settings", {"enforce_segregation_of_duties": "false", "contract_expiry_warning_days": "60"})

    # ---------------- Service Areas ----------------
    print("\n[Service Areas]")
    area = "Procurement %s" % SFX
    r = admin.post("/api/service-areas", {"name": area, "description": "Bens e servicos de procurement"})
    check("TC-SA-01", "criar service area", r.status_code == 200, r.text)
    area_id = r.json().get("id")
    r = admin.post("/api/service-areas", {"name": area})
    check("TC-SA-02", "nome duplicado e rejeitado (409)", r.status_code == 409, r.text)
    r = admin.post("/api/service-areas", {"name": "  "})
    check("TC-SA-03", "nome vazio e rejeitado (400)", r.status_code == 400, r.text)
    r = admin.put("/api/service-areas/%d" % area_id, {"name": area + " Svc", "description": "editada", "active": True})
    check("TC-SA-04", "renomear service area", r.status_code == 200, r.text)
    area += " Svc"
    check("TC-SA-05", "area nova aparece no formulario de vendors", area in admin.get("/vendors").text)
    check("TC-SA-06", "area nova aparece nos Approval Flows", area in admin.get("/settings/flows").text)

    tmp = admin.post("/api/service-areas", {"name": "Temp %s" % SFX}).json()["id"]
    r = admin.delete("/api/service-areas/%d" % tmp)
    check("TC-SA-07", "remover area nunca usada", r.status_code == 200, r.text)

    off = admin.post("/api/service-areas", {"name": "Inactive %s" % SFX, "active": False}).json()["id"]
    r = admin.vendor("Vendor InactiveArea %s" % SFX, off)
    check("TC-SA-08", "vendor numa area inactiva e rejeitado", r.status_code == 400, r.text)
    check("TC-SA-09", "area inactiva nao aparece no formulario de vendors",
          ("Inactive %s" % SFX) not in admin.get("/vendors").text)

    # ---------------- Departments / Roles ----------------
    print("\n[Roles / Departments]")
    dcode = "PROC%s" % SFX
    r = admin.post("/api/departments", {"code": dcode, "name": "Procurement Dept %s" % SFX, "hasOfficers": False})
    check("TC-RO-01", "criar departamento so com Approvers", r.status_code == 200, r.text)
    dept_id = r.json().get("id")
    html = admin.get("/settings/roles").text
    check("TC-RO-02", "perfil <CODE>_APPROVER criado, sem Officer",
          ("Procurement Dept %s Approver" % SFX) in html and ("Procurement Dept %s Officer" % SFX) not in html)
    r = admin.post("/api/departments", {"code": dcode, "name": "Outro %s" % SFX})
    check("TC-RO-03", "codigo duplicado e rejeitado (409)", r.status_code == 409, r.text)
    r = admin.post("/api/departments", {"code": "9x", "name": "Bad %s" % SFX})
    check("TC-RO-04", "codigo invalido e rejeitado", r.status_code == 400, r.text)
    r = admin.put("/api/departments/%d" % dept_id, {"name": "Procurement Dept %s" % SFX, "hasOfficers": True})
    check("TC-RO-05", "activar Officers cria o perfil Officer",
          r.status_code == 200 and ("Procurement Dept %s Officer" % SFX) in admin.get("/settings/roles").text, r.text)
    deps = re.findall(r'data-id="(\d+)" data-code="([A-Z0-9_]+)"', admin.get("/settings/roles").text)
    dep = {c: int(i) for i, c in deps}
    r = admin.put("/api/departments/%d" % dep["CS"], {"name": "Corporate Services", "active": False})
    check("TC-RO-06", "departamento de sistema (CS) nao pode ser desactivado", r.status_code == 400, r.text)
    r = admin.put("/api/departments/%d" % dep["LEGAL"], {"name": "Legal", "hasOfficers": False})
    check("TC-RO-07", "Legal nao pode ficar sem Officers", r.status_code == 400, r.text)

    roles = ids_from_select(admin.get("/settings/users").text, "u-role")
    need = ["System Administrator", "Corporate Services Officer", "Corporate Services Approver", "Legal Officer", "Legal Approver",
            "Compliance (AML & Regulatory) Approver", "Managing Director Approver", "UBA Staff (read-only)"]
    check("TC-RO-08", "perfis do BRD disponiveis em Users", all(n in roles for n in need), sorted(roles))

    # ---------------- Users ----------------
    print("\n[Users]")
    people = {"cso": "Corporate Services Officer", "csa": "Corporate Services Approver", "lgo": "Legal Officer",
              "lga": "Legal Approver", "lga2": "Legal Approver", "cmp": "Compliance (AML & Regulatory) Approver",
              "mda": "Managing Director Approver", "view": "UBA Staff (read-only)"}
    uid = {}
    for k, role in people.items():
        r = admin.post("/api/users", {"username": k + SFX, "fullName": "%s Test %s" % (k.upper(), SFX), "email": "%s%s@uba.test" % (k, SFX),
                                      "password": PASS, "roleId": roles[role]})
        uid[k] = r.json().get("id")
    check("TC-US-01", "criar utilizadores com cada perfil", all(uid.values()), uid)
    r = admin.post("/api/users", {"username": "x" + SFX, "fullName": "X", "email": "x%s@uba.test" % SFX, "password": PASS})
    check("TC-US-02", "criar utilizador sem perfil e rejeitado", r.status_code == 400, r.text)
    r = admin.put("/api/departments/%d" % dep["MD"], {"name": "Managing Director", "active": False})
    check("TC-RO-09", "desactivar departamento com utilizadores activos e bloqueado (409)", r.status_code == 409, r.text)

    S = {k: Session(k + SFX, PASS) for k in people}
    check("TC-US-03", "todos os utilizadores fazem login", all(s.ok for s in S.values()))

    # ---------------- Access control ----------------
    print("\n[Access control]")
    v = S["view"]
    check("TC-AC-01", "read-only: ve Vendors e Contracts", v.get("/vendors").status_code == 200 and v.get("/contracts").status_code == 200)
    check("TC-AC-02", "read-only: Settings bloqueado (403)", v.get("/settings/users").status_code == 403)
    check("TC-AC-03", "read-only: menu sem SETTINGS e sem 'Register new vendor'",
          "SETTINGS" not in v.get("/vendors").text and 'id="btn-new"' not in v.get("/vendors").text)
    check("TC-AC-04", "read-only: nao regista vendor (403)", v.vendor("X %s" % SFX, area_id).status_code == 403)
    check("TC-AC-05", "read-only: nao abre New contract (403)", v.get("/contracts/new").status_code == 403)
    for k in ["cso", "csa", "lgo", "lga"]:
        if S[k].get("/settings/flows").status_code != 403 or S[k].put("/api/flows/0", []).status_code != 403:
            check("TC-AC-06", "%s: Settings/API de config bloqueados" % k, False)
            break
    else:
        check("TC-AC-06", "nao-admins: Settings e APIs de configuracao bloqueados (403)", True)
    r = S["csa"].vendor("CSA Vendor %s" % SFX, area_id)
    check("TC-AC-07", "CS Approver nao regista vendors (403)", r.status_code == 403, r.text)
    r = S["lgo"].vendor("LGO Vendor %s" % SFX, area_id)
    check("TC-AC-08", "Legal Officer nao regista vendors (403)", r.status_code == 403, r.text)

    # ---------------- Vendor onboarding ----------------
    print("\n[Vendor onboarding]")
    r = S["cso"].vendor("Alpha Supplies %s" % SFX, area_id)
    check("TC-VO-01", "CS Officer regista vendor", r.status_code == 200, r.text)
    vid = r.json()["id"]
    r = S["cso"].post("/api/vendors/%d/review" % vid, {"action": "APPROVE"})
    check("TC-VO-02", "CS Officer nao aprova vendor (403)", r.status_code == 403, r.text)
    r = S["lga"].post("/api/vendors/%d/review" % vid, {"action": "APPROVE"})
    check("TC-VO-03", "Legal Approver nao aprova vendor (403)", r.status_code == 403, r.text)
    r = S["csa"].post("/api/vendors/%d/review" % vid, {"action": "START"})
    check("TC-VO-04", "CS Approver inicia revisao", r.status_code == 200, r.text)
    r = S["csa"].post("/api/vendors/%d/review" % vid, {"action": "APPROVE"})
    check("TC-VO-05", "CS Approver aprova vendor", r.status_code == 200, r.text)

    r = S["cso"].vendor("Beta Rejected %s" % SFX, area_id)
    vrej = r.json()["id"]
    r = S["csa"].post("/api/vendors/%d/review" % vrej, {"action": "REJECT"})
    check("TC-VO-06", "rejeitar sem motivo e bloqueado", r.status_code == 400, r.text)
    r = S["csa"].post("/api/vendors/%d/review" % vrej, {"action": "REJECT", "comment": "Documentacao incompleta"})
    check("TC-VO-07", "rejeitar com motivo", r.status_code == 200, r.text)
    r = S["cso"].contract(vrej, area_id, "Contrato vendor rejeitado")
    check("TC-VO-08", "nao cria contrato para vendor rejeitado", r.status_code == 400, r.text)

    admin.put("/api/settings", {"enforce_segregation_of_duties": "true", "contract_expiry_warning_days": "60"})
    vsod = admin.vendor("Gamma SoD %s" % SFX, area_id).json()["id"]
    r = admin.post("/api/vendors/%d/review" % vsod, {"action": "APPROVE"})
    check("TC-VO-09", "SoD activa: quem registou nao aprova o vendor (403)", r.status_code == 403, r.text)
    r = S["csa"].post("/api/vendors/%d/review" % vsod, {"action": "APPROVE"})
    check("TC-VO-10", "SoD activa: outro aprovador aprova", r.status_code == 200, r.text)

    # ---------------- Approval flow config ----------------
    print("\n[Approval flows]")
    flow_roles = {htmllib.unescape(n.strip()): int(i) for i, n in re.findall(r'<option value="(\d+)">([^<]+)</option>',
                                                          re.search(r'<template id="step-tpl">(.*?)</template>',
                                                                    admin.get("/settings/flows?scope=%d" % area_id).text, re.S).group(1))}
    LEGAL_A, COMP_A, MD_A = flow_roles["Legal Approver"], flow_roles["Compliance (AML & Regulatory) Approver"], flow_roles["Managing Director Approver"]
    check("TC-AF-01", "perfis Officer nao aparecem como aprovadores no fluxo",
          "Corporate Services Officer" not in flow_roles and "Legal Officer" not in flow_roles, sorted(flow_roles))
    r = admin.put("/api/flows/%d" % area_id, [{"name": "Legal Review", "approverRoleId": LEGAL_A, "approverUserId": uid["cmp"]}])
    check("TC-AF-02", "pessoa sem o perfil do passo e rejeitada", r.status_code == 400, r.text)
    r = admin.put("/api/flows/%d" % area_id, [{"name": "X", "approverRoleId": roles["Corporate Services Officer"]}])
    check("TC-AF-03", "perfil Officer como aprovador e rejeitado", r.status_code == 400, r.text)
    r = admin.put("/api/flows/%d" % area_id, [{"name": "X", "approverRoleId": flow_roles["Procurement Dept %s Approver" % SFX]}])
    check("TC-AF-04", "perfil sem utilizadores activos e rejeitado", r.status_code == 400, r.text)
    r = admin.put("/api/flows/%d" % area_id, [{"name": "X"}])
    check("TC-AF-05", "passo sem perfil e rejeitado", r.status_code == 400, r.text)
    r = admin.put("/api/flows/%d" % area_id, [
        {"name": "Legal Review", "approverRoleId": LEGAL_A},
        {"name": "Compliance Review", "approverRoleId": COMP_A, "approverUserId": uid["cmp"]},
        {"name": "MD Approval", "approverRoleId": MD_A}])
    check("TC-AF-06", "gravar fluxo de 3 passos (perfil / pessoa / perfil)", r.status_code == 200, r.text)
    check("TC-AF-07", "area mostra '3 step(s)' na lista", "3 step(s)" in admin.get("/settings/flows?scope=%d" % area_id).text)
    r = admin.delete("/api/service-areas/%d" % area_id)
    check("TC-SA-10", "nao remove area usada (409)", r.status_code == 409, r.text)

    # ---------------- Contract + approvals (happy path) ----------------
    print("\n[Contracts & approvals]")
    r = S["csa"].contract(vid, area_id, "Nao autorizado")
    check("TC-CT-01", "CS Approver nao cria contratos (403)", r.status_code == 403, r.text)
    r = S["lgo"].contract(vid, area_id, "Fornecimento de material %s" % SFX)
    check("TC-CT-02", "Legal Officer cria contrato", r.status_code == 200, r.text)
    cid = r.json()["id"]
    r = S["lgo"].post("/api/contracts/%d/submit" % cid)
    check("TC-CT-03", "Legal Officer submete contrato", r.status_code == 200, r.text)
    check("TC-CT-04", "contrato fica IN_APPROVAL", contract_status(admin, cid) == "IN_APPROVAL")

    t1 = pending_tasks(S["lga"], cid)
    check("TC-AP-01", "passo 1 (perfil Legal Approver) na caixa de lga", len(t1) == 1, t1)
    check("TC-AP-02", "passo 1 tambem na caixa de lga2 (mesmo perfil)", pending_tasks(S["lga2"], cid) == t1)
    check("TC-AP-03", "passo 1 nao aparece a cmp", pending_tasks(S["cmp"], cid) == [])
    r = S["cmp"].post("/api/approvals/tasks/%d/decide" % t1[0], {"decision": "APPROVE"})
    check("TC-AP-04", "outro perfil nao decide (403)", r.status_code == 403, r.text)
    r = S["lga"].post("/api/approvals/tasks/%d/decide" % t1[0], {"decision": "APPROVE", "comment": "OK legal"})
    check("TC-AP-05", "lga aprova passo 1", r.status_code == 200, r.text)
    check("TC-AP-06", "historico regista quem decidiu", ("LGA Test %s" % SFX) in admin.get("/contracts/%d" % cid).text)
    check("TC-AP-07", "passo 1 sai da caixa de lga2", pending_tasks(S["lga2"], cid) == [])

    t2 = pending_tasks(S["cmp"], cid)
    check("TC-AP-08", "passo 2 (pessoa cmp) na caixa de cmp", len(t2) == 1, t2)
    r = S["lga"].post("/api/approvals/tasks/%d/decide" % t2[0], {"decision": "APPROVE"})
    check("TC-AP-09", "outra pessoa nao decide passo atribuido a cmp (403)", r.status_code == 403, r.text)
    r = S["cmp"].post("/api/approvals/tasks/%d/decide" % t2[0], {"decision": "APPROVE"})
    check("TC-AP-10", "cmp aprova passo 2", r.status_code == 200, r.text)

    t3 = pending_tasks(S["mda"], cid)
    check("TC-AP-11", "passo 3 (perfil MD) na caixa de mda", len(t3) == 1, t3)
    r = S["lga"].post("/api/approvals/tasks/%d/reassign" % t3[0], {"toUserId": uid["lga"], "reason": "x"})
    check("TC-AP-12", "quem nao tem o perfil nao reatribui (403)", r.status_code == 403, r.text)
    r = S["mda"].post("/api/approvals/tasks/%d/reassign" % t3[0], {"toUserId": uid["lga2"], "reason": ""})
    check("TC-AP-13", "reatribuir sem motivo e bloqueado", r.status_code == 400, r.text)
    r = S["mda"].post("/api/approvals/tasks/%d/reassign" % t3[0], {"toUserId": uid["lga2"], "reason": "MD de ferias"})
    check("TC-AP-14", "mda reatribui o passo 3 a lga2", r.status_code == 200, r.text)
    check("TC-AP-15", "apos reatribuir: sai da caixa do MD e entra na de lga2",
          pending_tasks(S["mda"], cid) == [] and pending_tasks(S["lga2"], cid) == t3)
    check("TC-AP-16", "historico mostra reatribuicao a partir da fila do perfil", "role queue" in admin.get("/contracts/%d" % cid).text)
    r = S["lga2"].post("/api/approvals/tasks/%d/decide" % t3[0], {"decision": "APPROVE"})
    check("TC-AP-17", "lga2 aprova o ultimo passo", r.status_code == 200, r.text)
    check("TC-AP-18", "contrato fica ACTIVE apos o ultimo passo", contract_status(admin, cid) == "ACTIVE")
    r = S["lga2"].post("/api/approvals/tasks/%d/decide" % t3[0], {"decision": "APPROVE"})
    check("TC-AP-19", "decidir de novo a mesma tarefa e bloqueado", r.status_code == 400, r.text)

    # ---------------- Return / Reject ----------------
    print("\n[Return / Reject]")
    c2 = S["cso"].contract(vid, area_id, "Contrato devolvido %s" % SFX).json()["id"]
    S["cso"].post("/api/contracts/%d/submit" % c2)
    t = pending_tasks(S["lga"], c2)[0]
    r = S["lga"].post("/api/approvals/tasks/%d/decide" % t, {"decision": "RETURN"})
    check("TC-RR-01", "devolver sem comentario e bloqueado", r.status_code == 400, r.text)
    r = S["lga"].post("/api/approvals/tasks/%d/decide" % t, {"decision": "RETURN", "comment": "Corrigir valor"})
    check("TC-RR-02", "devolver com comentario -> contrato DRAFT", r.status_code == 200 and contract_status(admin, c2) == "DRAFT", r.text)
    r = S["cso"].post("/api/contracts/%d/submit" % c2)
    check("TC-RR-03", "re-submeter apos devolucao cria novo pedido", r.status_code == 200, r.text)
    t = pending_tasks(S["lga"], c2)[0]
    r = S["lga"].post("/api/approvals/tasks/%d/decide" % t, {"decision": "REJECT", "comment": "Fora do orcamento"})
    check("TC-RR-04", "rejeitar -> contrato REJECTED, passos seguintes cancelados",
          r.status_code == 200 and contract_status(admin, c2) == "REJECTED", r.text)

    # ---------------- Default flow / admin ----------------
    print("\n[Default flow]")
    c3 = admin.contract(vid, area_id, "Admin override %s" % SFX).json()["id"]
    admin.put("/api/settings", {"enforce_segregation_of_duties": "false", "contract_expiry_warning_days": "60"})
    admin.post("/api/contracts/%d/submit" % c3)
    t = all_pending_task(admin, c3)
    r = admin.post("/api/approvals/tasks/%d/decide" % t, {"decision": "APPROVE"})
    check("TC-DF-01", "SYS_ADMIN pode decidir qualquer passo (override)", r.status_code == 200, r.text)

    # ---------------- Immediate effect of role / status changes ----------------
    print("\n[Role / status changes]")
    admin.put("/api/users/%d" % uid["cso"], {"roleId": roles["UBA Staff (read-only)"]})
    r = S["cso"].vendor("After demotion %s" % SFX, area_id)
    check("TC-US-04", "mudanca de perfil tem efeito imediato (sem novo login)", r.status_code == 403, r.text)
    admin.put("/api/users/%d" % uid["view"], {"active": False})
    r = S["view"].get("/vendors")
    check("TC-US-05", "utilizador desactivado perde a sessao", r.status_code in (302, 401) and "login" in r.headers.get("Location", "login"),
          r.status_code)
    check("TC-US-06", "utilizador desactivado nao faz login", not Session("view" + SFX, PASS).ok)
    me = re.search(r'data-id="(\d+)"[^>]*data-username="' + ADMIN_USER + '"', admin.get("/settings/users").text)
    if me:
        r = admin.put("/api/users/%s" % me.group(1), {"roleId": roles["UBA Staff (read-only)"]})
        check("TC-US-07", "nao e possivel retirar o ultimo administrador", r.status_code == 400 or "pelo menos" in r.text, r.text)

    vpend = admin.vendor("Pending Delta %s" % SFX, area_id).json()["id"]
    admin.put("/api/service-areas/%d" % area_id, {"name": area, "active": False})
    check("TC-SA-11", "area desactivada sai dos Approval Flows", area not in admin.get("/settings/flows").text)
    check("TC-SA-12", "vendor existente mantem a area desactivada", area in admin.get("/vendors/%d" % vid).text)
    page = admin.get("/vendors/%d" % vpend).text
    check("TC-SA-13", "edicao de vendor mostra a area desactivada como opcao '(inactive)'", (area + " (inactive)") in page)
    r = admin.put("/api/vendors/%d" % vpend, {"supplierName": "Pending Delta %s Lda" % SFX, "email": "d@test.co.mz", "mobile": "840000002",
                                              "address": "Maputo", "nuit": re.search(r'(\d{9})', page.split("NUIT", 1)[1]).group(1),
                                              "serviceCategoryId": area_id})
    check("TC-SA-14", "editar vendor mantendo a area desactivada e permitido", r.status_code == 200, r.text)

    # ---------------- Summary ----------------
    failed = [r for r in results if not r[2]]
    print("\n%d checks, %d passed, %d failed" % (len(results), len(results) - len(failed), len(failed)))
    for tc, desc, _ in failed:
        print("  FAILED %s %s" % (tc, desc))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
