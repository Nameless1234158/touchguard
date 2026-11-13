#!/usr/bin/env python3
"""ghosttouch - calibra, mapeia e monitora toques fantasmas.
Roda no Termux, lendo a tela por ADB (Depuração por Wi-Fi). Só observa: não bloqueia toques."""
import csv, json, os, re, statistics, subprocess, sys, time

DIR = os.path.dirname(os.path.abspath(__file__))
PADRAO = os.path.join(DIR, "padrao.json")
LOG = os.path.join(DIR, "log.csv")

# ---- ajustes (mexa aqui se estiver detectando de menos ou demais) ----
GRID = 40            # tela dividida em GRID x GRID células
JANELA = 3.0         # segundos observados
MIN_TOQUES = 8       # toques na janela pra considerar rajada
MAX_CELULAS = 4      # rajada concentrada em até N células = suspeita
DUR_CURTA = 0.04     # toque mais curto que isso (s) = suspeito
FRACAO_PADRAO = 0.6  # % dos toques caindo no padrão salvo = suspeita
COOLDOWN = 45        # s entre uma detecção e outra
ESCALAR_EM = 150     # s: nova rajada logo após o reset => escala (reboot)


def adb(*args, timeout=15):
    try:
        r = subprocess.run(["adb", "shell", *args], capture_output=True,
                           text=True, timeout=timeout)
        return r.stdout
    except Exception:
        return ""


def garantir_adb():
    try:
        out = subprocess.run(["adb", "devices"], capture_output=True,
                             text=True, timeout=15).stdout
    except Exception:
        sys.exit("adb não encontrado. Rode: pkg install android-tools")
    if not any(l.split()[-1:] == ["device"] for l in out.splitlines()[1:]):
        sys.exit("ADB não conectado. Ligue a Depuração por Wi-Fi e rode:\n"
                 "  adb connect localhost:PORTA")


def achar_touch():
    disp, info = None, {}
    for l in adb("getevent", "-lp").splitlines():
        m = re.match(r"add device \d+: (\S+)", l)
        if m:
            disp = m.group(1)
            info[disp] = {"nome": "", "max": {}}
            continue
        if disp is None:
            continue
        n = re.search(r'name:\s+"([^"]*)"', l)
        if n:
            info[disp]["nome"] = n.group(1)
        mm = re.search(r"(ABS_MT_POSITION_[XY])\s*:.*max (\d+)", l)
        if mm:
            info[disp]["max"][mm.group(1)] = int(mm.group(2))
    cands = [(d, v) for d, v in info.items() if len(v["max"]) == 2]
    if not cands:
        sys.exit("Não achei a tela de toque. O ADB está conectado?")
    cands.sort(key=lambda c: "touchscreen" not in c[1]["nome"].lower())
    d, v = cands[0]
    return d, v["max"]["ABS_MT_POSITION_X"], v["max"]["ABS_MT_POSITION_Y"]


def eventos(dev, mx, my, limite=None):
    cmd = ["adb", "shell"] + (["timeout", str(limite)] if limite else []) \
        + ["getevent", "-l", dev]
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, text=True)
    slot, st = 0, {}
    try:
        for linha in p.stdout:
            c = linha.split()
            if len(c) < 4:
                continue
            code, val = c[2], c[3]
            try:
                if code == "ABS_MT_SLOT":
                    slot = int(val, 16)
                elif code == "ABS_MT_TRACKING_ID":
                    if val == "ffffffff":
                        s = st.pop(slot, None)
                        if s and "x" in s and "y" in s:
                            yield {"x": s["x"] / mx, "y": s["y"] / my,
                                   "dur": time.time() - s["t0"]}
                    else:
                        st[slot] = {"t0": time.time()}
                elif code == "ABS_MT_POSITION_X":
                    st.setdefault(slot, {"t0": time.time()})["x"] = int(val, 16)
                elif code == "ABS_MT_POSITION_Y":
                    st.setdefault(slot, {"t0": time.time()})["y"] = int(val, 16)
            except ValueError:
                pass
    finally:
        p.kill()


def celula(t):
    return (min(int(t["x"] * GRID), GRID - 1), min(int(t["y"] * GRID), GRID - 1))


def carregar():
    try:
        return json.load(open(PADRAO))
    except Exception:
        return {}


def salvar(p):
    json.dump(p, open(PADRAO, "w"))


def aprender(pad, cels):
    for c in cels:
        k = f"{c[0]},{c[1]}"
        pad[k] = pad.get(k, 0) + 1


def no_padrao(c, pad):
    return any(f"{c[0]+dx},{c[1]+dy}" in pad for dx in (-1, 0, 1) for dy in (-1, 0, 1))


def notificar(msg):
    try:
        subprocess.run(["termux-notification", "--title", "Toque fantasma",
                        "--content", msg], timeout=10)
    except Exception:
        pass


def teclado_aberto():
    out = adb("dumpsys", "input_method", timeout=20)
    return "mInputShown=true" in out or "mIsInputViewShown=true" in out


def registrar(n, cels, med, kb):
    novo = not os.path.exists(LOG)
    with open(LOG, "a", newline="") as f:
        w = csv.writer(f)
        if novo:
            w.writerow(["data_hora", "toques", "celulas_distintas",
                        "duracao_mediana_s", "teclado_aberto"])
        w.writerow([time.strftime("%Y-%m-%d %H:%M:%S"), n, len(cels),
                    round(med, 3), int(kb)])


def analisar(jan, pad):
    cels = {j[1] for j in jan}
    med = statistics.median(j[2] for j in jan)
    frac = sum(no_padrao(j[1], pad) for j in jan) / len(jan)
    por_padrao = bool(pad) and frac >= FRACAO_PADRAO
    por_forma = len(cels) <= MAX_CELULAS or med < DUR_CURTA
    return por_padrao, por_forma, cels, med


# ---------------- comandos ----------------
def calibrar(seg=45):
    garantir_adb()
    dev, mx, my = achar_touch()
    print(f"Calibrando por {seg}s. NÃO encosta na tela!")
    pad, n = carregar(), 0
    for t in eventos(dev, mx, my, seg):
        aprender(pad, [celula(t)])
        n += 1
    salvar(pad)
    print(f"Pronto: {n} toques sozinhos gravados." if n
          else "Nenhum toque fantasma agora (a tela está calma).")


def monitor(reset=False, reboot=False):
    garantir_adb()
    dev, mx, my = achar_touch()
    pad = carregar()
    jan, ultimo, ultimo_reset = [], 0, 0
    print("Monitorando... (Ctrl+C pra parar)")
    try:
        for t in eventos(dev, mx, my):
            agora = time.time()
            jan.append((agora, celula(t), t["dur"]))
            jan = [j for j in jan if agora - j[0] <= JANELA]
            if len(jan) < MIN_TOQUES or agora - ultimo < COOLDOWN:
                continue
            por_padrao, por_forma, cels, med = analisar(jan, pad)
            if not (por_padrao or por_forma):
                continue
            kb = teclado_aberto()
            if kb and not por_padrao:   # digitando: não confunde com bug
                jan = []
                continue
            ultimo = agora
            registrar(len(jan), cels, med, kb)
            if not kb:
                aprender(pad, [j[1] for j in jan])
                salvar(pad)
            notificar(f"Rajada detectada: {len(jan)} toques")
            print(time.strftime("%H:%M:%S"), "rajada de", len(jan), "toques")
            if reset and not kb:        # nunca age com o teclado aberto
                if reboot and agora - ultimo_reset < ESCALAR_EM:
                    notificar("Ainda com toques. Reiniciando...")
                    adb("svc", "power", "reboot")
                else:
                    adb("input", "keyevent", "26")
                    time.sleep(2)
                    adb("input", "keyevent", "26")
                    ultimo_reset = time.time()
            jan = []
    except KeyboardInterrupt:
        print("\nParado.")


def mapa():
    pad = carregar()
    if not pad:
        print("Padrão vazio. Rode a calibração primeiro.")
        return
    mx = max(pad.values())
    chars = " .:*#"
    print("Mapa de calor (topo = topo da tela)")
    for y in range(0, GRID, 2):
        linha = ""
        for x in range(GRID):
            v = pad.get(f"{x},{y}", 0) + pad.get(f"{x},{y+1}", 0)
            linha += chars[0 if v == 0 else min(4, 1 + int(3 * v / (2 * mx)))]
        print("|" + linha + "|")


def status():
    pad = carregar()
    if not pad:
        print("Padrão vazio.")
        return
    print(f"{len(pad)} células marcadas, {sum(pad.values())} toques no total.")
    print("Pontos mais ativos (x%, y% da tela):")
    for k, v in sorted(pad.items(), key=lambda i: -i[1])[:5]:
        x, y = map(int, k.split(","))
        print(f"  x={round(100*(x+.5)/GRID)}%  y={round(100*(y+.5)/GRID)}%  -> {v} toques")


def relatorio():
    if not os.path.exists(LOG):
        print("Sem registros ainda.")
        return
    rows = list(csv.reader(open(LOG)))[1:]
    if not rows:
        print("Sem registros ainda.")
        return
    toques = [int(r[1]) for r in rows]
    print(f"Crises registradas: {len(rows)}")
    print(f"Primeira: {rows[0][0]}   Última: {rows[-1][0]}")
    print(f"Toques por crise: média {sum(toques)//len(toques)}, maior {max(toques)}")
    print(f"Detalhes em: {LOG}")


def limpar():
    for f in (PADRAO,):
        if os.path.exists(f):
            os.remove(f)
    print("Padrão apagado. O log.csv foi mantido (serve de prova).")


AJUDA = """uso: ghost.py <comando>
  calibrar [seg]            grava toques sozinhos (celular parado). padrão 45s
  monitor                   vigia, notifica e registra rajadas
  monitor --reset           + apaga/acende a tela quando detectar
  monitor --reset --reboot  + reinicia se a rajada voltar logo depois
  mapa                      mapa de calor do padrão salvo
  status                    pontos mais ativos
  relatorio                 resumo do log de crises
  limpar                    apaga o padrão salvo"""

if __name__ == "__main__":
    a = sys.argv[1:]
    cmd = a[0] if a else ""
    if cmd == "calibrar":
        calibrar(int(a[1]) if len(a) > 1 else 45)
    elif cmd == "monitor":
        monitor("--reset" in a, "--reboot" in a)
    elif cmd == "mapa":
        mapa()
    elif cmd == "status":
        status()
    elif cmd == "relatorio":
        relatorio()
    elif cmd == "limpar":
        limpar()
    else:
        print(AJUDA)
