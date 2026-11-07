from pathlib import Path

import cadquery as cq

OUT = Path(__file__).resolve().parent
STL_DIR = OUT / "STL"

TOL = 0.3
WALL = 1.6
FLOOR = 1.2
LID_T = 1.2
REBATE = 0.8
LID_GAP = 0.12
R_OUT = 5.0
R_IN = 1.0

BATT = (30.0, 15.0, 5.0)
ESP = (18.0, 22.52, 1.0)
ESP_USB_H = 3.3
CHG = (20.0, 25.0, 1.0)
MPU = (16.0, 20.0, 1.6)
MPU_PARTS_H = 1.2
MPU_HOLE_SPACING = 15.24
MPU_HOLE_EDGE = 2.5
INSERT_OD = 3.2
INSERT_L = 3.0
INSERT_HOLE = INSERT_OD - 0.2
INSERT_SPARE = 0.2
BOTTOM_SKIN = 0.4
BOSS_OD = INSERT_OD + 2 * 1.2
BOSS_H = INSERT_L + INSERT_SPARE - (FLOOR - BOTTOM_SKIN)

BOARD_Z = BATT[2] + 0.4
assert BOSS_H + MPU[2] + MPU_PARTS_H <= BOARD_Z - 0.1, (
    f"GY-521 on {BOSS_H:.1f} mm bosses would hit the charger above it: "
    "use a shorter insert (INSERT_L) or a taller case")
EXTRA_L = 0.0
EXTRA_W = 0.0
EXTRA_H = 0.0

INNER_H = BOARD_Z + ESP[2] + ESP_USB_H + 0.3 + EXTRA_H

TAB_SPACE = 1.4

ESP_X0 = TAB_SPACE
CHG_X0 = ESP_X0 + ESP[0] + TOL + 0.3
BATT_X0 = 0.5
RIB_X0 = BATT_X0 + BATT[0] + TOL
RIB_T = 0.8
MPU_X0 = RIB_X0 + RIB_T + 0.2
INNER_L = max(MPU_X0 + MPU[0] + TOL, CHG_X0 + CHG[0] + TOL + 9.4) + EXTRA_L
INNER_W = CHG[1] + 2 * TOL + EXTRA_W

OUT_L = INNER_L + 2 * WALL
OUT_W = INNER_W + 2 * WALL
OUT_H = FLOOR + INNER_H + LID_T

BAY_X0 = CHG_X0 + CHG[0] + TOL
BAY_CX = (BAY_X0 + INNER_L - TAB_SPACE) / 2

USB_W, USB_H = 9.6, 3.8
USB_Z = BOARD_Z + ESP[2] + ESP_USB_H / 2
ESP_USB_X = ESP_X0 + ESP[0] / 2
CHG_USB_X = CHG_X0 + CHG[0] / 2

BTN_HOLE = 5.5
BTN_SOCKET = 7.0
BTN_SOCKET_DEPTH = 3.4
UI_X = BAY_CX + 0.3
BTN_POS = (UI_X, 5.0)
LED_D = 5.0
LED_POS = (UI_X, 13.3)
SW_SIZE = (8.0, 3.0)
SW_SLOT = (SW_SIZE[0] + 0.4, SW_SIZE[1] + 0.4)
SW_POS = (UI_X, 20.8)
BUZZ_D = 12.0
BUZZ_RECESS = 0.4
BUZZ_POS = (ESP_X0 + ESP[0] / 2, 14.0)

SNAP_W, SNAP_DEPTH, SNAP_T, SNAP_BUMP = 6.0, 2.6, 0.8, 0.35

SLV_WALL = 1.4
SLV_BOTTOM = 1.0
SLV_LIP = 2.0
SLV_LIP_T = 1.2
SLV_FIT = 0.05
BRIDGE_W = 8.0
SUPPORT_GAP = 0.4
SUPPORT_T = 1.2
SLV_LIP_WIDE = 2.6
SW_NOTCH_MARGIN = 1.5
LUG_GAP = 20.0
LUG_T = 3.0
LUG_LEN = 5.2
LUG_ROOT = 2.6
LUG_H = 5.6
BAR_HOLE = 1.1
BAR_HOLE_DEPTH = 1.8
BAR_OFFSET = 2.7
BAR_Z = 2.8
SLOT_W = 26.5
SLOT_T = 3.2
SLOT_SIDE = 2.4
SLOT_BAR = 2.6
SLOT_LEN = 6.6
SLOT_BAR_T = 3.0


def rounded_box(l, w, h, r, x0=0.0, y0=0.0, z0=0.0):
    return (cq.Workplane("XY").workplane(offset=z0)
            .center(x0 + l / 2, y0 + w / 2)
            .rect(l, w).extrude(h)
            .edges("|Z").fillet(r))


def stadium_y_wall(cx, cz, w, h, y0, depth):
    return (cq.Workplane("XZ", origin=(0, y0 + depth, 0))
            .center(cx, cz).slot2D(w, h).extrude(depth + 0.2))


def make_case():
    body = rounded_box(OUT_L, OUT_W, OUT_H, R_OUT, -WALL, -WALL, -FLOOR)
    body = body.faces("<Z").edges().fillet(2.0).faces(">Z").edges().fillet(0.6)
    body = body.cut(rounded_box(INNER_L, INNER_W, INNER_H + LID_T + 1, R_IN))
    body = body.cut(rounded_box(INNER_L + 2 * REBATE, INNER_W + 2 * REBATE, LID_T + 1,
                                R_IN + REBATE, -REBATE, -REBATE, INNER_H))

    hx = MPU_X0 + MPU[0] - MPU_HOLE_EDGE
    for dy in (-MPU_HOLE_SPACING / 2, MPU_HOLE_SPACING / 2):
        boss = (cq.Workplane("XY").center(hx, INNER_W / 2 + dy)
                .circle(BOSS_OD / 2).extrude(BOSS_H))
        body = body.union(boss)
        body = body.cut(cq.Workplane("XY").workplane(offset=-FLOOR + BOTTOM_SKIN)
                        .center(hx, INNER_W / 2 + dy).circle(INSERT_HOLE / 2)
                        .extrude(BOSS_H + FLOOR))

    for ux in (ESP_USB_X, CHG_USB_X):
        body = body.cut(stadium_y_wall(ux, USB_Z, USB_W, USB_H, -WALL - 0.1, WALL + 0.3))

    gz = INNER_H - SNAP_DEPTH + 0.9
    for x in (-0.6, INNER_L - 0.4):
        body = body.cut(cq.Workplane("XY").box(1.0, SNAP_W + 0.6, 1.0)
                        .translate((x + 0.5, INNER_W / 2, gz)))

    return body


def make_lid():
    lid = rounded_box(INNER_L + 2 * (REBATE - LID_GAP), INNER_W + 2 * (REBATE - LID_GAP),
                      LID_T, R_IN + REBATE - LID_GAP,
                      -REBATE + LID_GAP, -REBATE + LID_GAP, INNER_H)
    lid = lid.faces(">Z").edges().fillet(0.5)

    sock = (cq.Workplane("XY").workplane(offset=INNER_H - BTN_SOCKET_DEPTH)
            .center(*BTN_POS).rect(BTN_SOCKET + 1.6, BTN_SOCKET + 1.6)
            .extrude(BTN_SOCKET_DEPTH))
    lid = lid.union(sock)
    lid = lid.cut(cq.Workplane("XY").workplane(offset=INNER_H - BTN_SOCKET_DEPTH - 0.1)
                  .center(*BTN_POS).rect(BTN_SOCKET, BTN_SOCKET)
                  .extrude(BTN_SOCKET_DEPTH + 0.1))
    lid = lid.cut(cq.Workplane("XY").workplane(offset=INNER_H - 0.1)
                  .center(*BTN_POS).rect(BTN_HOLE, BTN_HOLE).extrude(LID_T + 0.2))
    lid = lid.cut(cq.Workplane("XY").workplane(offset=INNER_H - 0.1)
                  .center(*LED_POS).circle(LED_D / 2).extrude(LID_T + 0.2))
    lid = lid.cut(cq.Workplane("XY").workplane(offset=INNER_H - 0.1)
                  .center(*SW_POS).rect(*SW_SLOT)
                  .extrude(LID_T + 0.2))
    lid = lid.cut(cq.Workplane("XY").workplane(offset=INNER_H - 0.1)
                  .center(*BUZZ_POS).circle(BUZZ_D / 2 + 0.2).extrude(BUZZ_RECESS + 0.1))
    grille = [(0, 0)] + [(3.2 * c, 3.2 * s) for c, s in
                         ((1, 0), (0.5, 0.866), (-0.5, 0.866), (-1, 0), (-0.5, -0.866), (0.5, -0.866))]
    lid = lid.cut(cq.Workplane("XY").workplane(offset=INNER_H)
                  .pushPoints([(BUZZ_POS[0] + x, BUZZ_POS[1] + y) for x, y in grille])
                  .circle(0.9).extrude(LID_T + 0.2))

    for x, sgn in ((0.1, -1), (INNER_L - 0.1 - SNAP_T, 1)):
        tab = (cq.Workplane("XY").box(SNAP_T, SNAP_W, SNAP_DEPTH, centered=False)
               .translate((x, INNER_W / 2 - SNAP_W / 2, INNER_H - SNAP_DEPTH)))
        bump = (cq.Workplane("XY").box(SNAP_BUMP + 0.01, SNAP_W, 0.8, centered=False)
                .translate((x - SNAP_BUMP if sgn < 0 else x + SNAP_T,
                            INNER_W / 2 - SNAP_W / 2, INNER_H - SNAP_DEPTH + 0.5)))
        lid = lid.union(tab).union(bump)
    return lid


def make_sleeve(strap="lugs", retain="bridge"):
    il, iw = OUT_L + 2 * SLV_FIT, OUT_W + 2 * SLV_FIT
    x0, y0 = -WALL - SLV_FIT, -WALL - SLV_FIT
    h = SLV_BOTTOM + OUT_H + 0.1 + SLV_LIP_T
    zb = -FLOOR - SLV_BOTTOM
    shell = rounded_box(il + 2 * SLV_WALL, iw + 2 * SLV_WALL, h, R_OUT + SLV_WALL + SLV_FIT,
                        x0 - SLV_WALL, y0 - SLV_WALL, zb)
    shell = shell.faces("<Z").edges().fillet(2.2).faces(">Z").edges().fillet(0.6)
    shell = shell.cut(rounded_box(il, iw, OUT_H + 0.1, R_OUT + SLV_FIT, x0, y0, -FLOOR))
    lip = SLV_LIP_WIDE if retain == "wide" else SLV_LIP
    shell = shell.cut(rounded_box(il - 2 * lip, iw - 2 * lip, 5,
                                  R_OUT + SLV_FIT - lip, x0 + lip, y0 + lip, OUT_H - 2))
    pod_top = OUT_H - FLOOR
    pocket_plus = rounded_box(il + 0.6, iw + 0.6, h + 2, R_OUT + SLV_FIT + 0.3, x0 - 0.3, y0 - 0.3, zb - 1)
    nx0 = SW_POS[0] + SW_SLOT[0] / 2 - 0.5
    nx1 = x0 + il
    ny0 = SW_POS[1] - SW_SLOT[1] / 2 - SW_NOTCH_MARGIN
    ny1 = SW_POS[1] + SW_SLOT[1] / 2 + SW_NOTCH_MARGIN
    shell = shell.cut(cq.Workplane("XY").box(nx1 - nx0, ny1 - ny0, SLV_LIP_T + 2, centered=False)
                      .translate((nx0, ny0, pod_top + 0.05)))
    if retain == "bridge":
        bx = BRIDGE_X
        band = (cq.Workplane("XY")
                .box(BRIDGE_W, iw + 2 * SLV_WALL - 1.0, SLV_LIP_T)
                .edges("|X").fillet(0.5)
                .translate((bx, y0 + iw / 2, pod_top + 0.1 + SLV_LIP_T / 2)))
        shell = shell.union(band.intersect(pocket_plus))
    shell = shell.cut(rounded_box(il - 12, iw - 10, 3, 4, x0 + 6, y0 + 5, zb - 1))
    for ux in (ESP_USB_X, CHG_USB_X):
        shell = shell.cut(stadium_y_wall(ux, USB_Z, 12.5, 7.0, y0 - SLV_WALL - 0.2, SLV_WALL + 0.6))

    cy = y0 + iw / 2
    for sgn in (-1, 1):
        xe = x0 - SLV_WALL if sgn < 0 else x0 + il + SLV_WALL
        if strap == "loop":
            bar = (cq.Workplane("XY")
                   .box(SLOT_LEN + LUG_ROOT, SLOT_W + 2 * SLOT_SIDE, SLOT_BAR_T)
                   .edges("|Z").fillet(2.0)
                   .translate((xe + sgn * (SLOT_LEN - LUG_ROOT) / 2, cy, zb + SLOT_BAR_T / 2)))
            slot = (cq.Workplane("XY")
                    .center(xe + sgn * (SLOT_LEN - SLOT_BAR - SLOT_T / 2), cy)
                    .rect(SLOT_T, SLOT_W).extrude(SLOT_BAR_T + 2)
                    .edges("|Z").fillet(SLOT_T / 2 - 0.05)
                    .translate((0, 0, zb - 1)))
            shell = shell.union(bar.cut(slot))
            continue
        bar_x = xe + sgn * BAR_OFFSET
        for side in (-1, 1):
            ly = cy + side * (LUG_GAP / 2 + LUG_T / 2)
            lug = (cq.Workplane("XY")
                   .box(LUG_LEN + LUG_ROOT, LUG_T, LUG_H)
                   .edges("|Y and %sX" % (">" if sgn > 0 else "<")).fillet(LUG_H / 2 - 0.3)
                   .translate((xe + sgn * (LUG_LEN - LUG_ROOT) / 2, ly, zb + LUG_H / 2)))
            hole = (cq.Workplane("XZ", origin=(0, cy + side * LUG_GAP / 2, 0))
                    .center(bar_x, zb + BAR_Z).circle(BAR_HOLE / 2)
                    .extrude(side * -BAR_HOLE_DEPTH - side * 0.01))
            shell = shell.union(lug).cut(hole)
    shell = shell.cut(rounded_box(il, iw, OUT_H + 0.1, R_OUT + SLV_FIT, x0, y0, -FLOOR))

    return shell


BRIDGE_X = (BUZZ_POS[0] + BUZZ_D / 2 + BAY_X0) / 2


def make_bridge_support():
    iw = OUT_W + 2 * SLV_FIT
    y0 = -WALL - SLV_FIT
    zb = -FLOOR - SLV_BOTTOM
    top = OUT_H - FLOOR + 0.1 - SUPPORT_GAP
    ya, yb = y0 + 5 + 0.6, y0 + iw - 5 - 0.6
    xa, xb = BRIDGE_X - BRIDGE_W / 2 + 0.6, BRIDGE_X + BRIDGE_W / 2 - 0.6
    walls = None
    for x in (xa, BRIDGE_X, xb):
        w = cq.Workplane("XY").box(SUPPORT_T, yb - ya, top - zb, centered=False).translate((x - SUPPORT_T / 2, ya, zb))
        walls = w if walls is None else walls.union(w)
    for y in (ya, yb - SUPPORT_T):
        walls = walls.union(cq.Workplane("XY").box(xb - xa + SUPPORT_T, SUPPORT_T, top - zb, centered=False)
                            .translate((xa - SUPPORT_T / 2, y, zb)))
    return walls


def dummies():
    parts = {
        "battery": cq.Workplane("XY").box(*BATT, centered=False).translate((BATT_X0, TOL, 0)),
        "mpu6050": cq.Workplane("XY").box(MPU[0], MPU[1], MPU[2] + MPU_PARTS_H, centered=False)
                   .translate((MPU_X0, (INNER_W - MPU[1]) / 2, BOSS_H)),
        "esp32c3": cq.Workplane("XY").box(ESP[0], ESP[1], ESP[2] + 1.2, centered=False)
                   .translate((ESP_X0, 0.3, BOARD_Z))
                   .union(cq.Workplane("XY").box(8.9, 7.3, ESP_USB_H, centered=False)
                          .translate((ESP_USB_X - 4.45, -0.4, BOARD_Z + ESP[2]))),
        "charger": cq.Workplane("XY").box(CHG[0], CHG[1], CHG[2] + 1.2, centered=False)
                   .translate((CHG_X0, TOL, BOARD_Z))
                   .union(cq.Workplane("XY").box(8.9, 7.3, ESP_USB_H, centered=False)
                          .translate((CHG_USB_X - 4.45, -0.4, BOARD_Z + CHG[2]))),
    }
    return parts


def main():
    STL_DIR.mkdir(exist_ok=True)
    case, lid = make_case(), make_lid()
    sleeve, sleeve_loop = make_sleeve(), make_sleeve("loop")
    wide, wide_loop = make_sleeve(retain="wide"), make_sleeve("loop", retain="wide")
    support = make_bridge_support()
    for name, part in (("pod_case", case), ("pod_lid", lid), ("pod_sleeve", sleeve),
                       ("pod_sleeve_loop", sleeve_loop), ("pod_sleeve_nobridge", wide),
                       ("pod_sleeve_loop_nobridge", wide_loop)):
        cq.exporters.export(part, str(OUT / f"{name}.step"))
        mesh = part
        if name in ("pod_sleeve", "pod_sleeve_loop"):
            mesh = cq.Workplane("XY").add(cq.Compound.makeCompound([part.val(), support.val()]))
            cq.exporters.export(mesh, str(OUT / f"{name}_with_support.step"))
        cq.exporters.export(mesh, str(STL_DIR / f"{name}.stl"), tolerance=0.02, angularTolerance=0.15)

    asm = cq.Assembly(name="korgan_pod")
    asm.add(case, name="case", color=cq.Color(0.25, 0.27, 0.30))
    asm.add(lid, name="lid", color=cq.Color(0.35, 0.37, 0.40))
    asm.add(sleeve, name="sleeve", color=cq.Color(0.20, 0.45, 0.65, 0.6))
    for i, (n, p) in enumerate(dummies().items()):
        asm.add(p, name=n, color=cq.Color(*[(0.85, 0.55, 0.2), (0.2, 0.6, 0.3),
                                            (0.2, 0.3, 0.8), (0.7, 0.2, 0.2)][i]))
    asm.save(str(OUT / "pod_assembly.step"))

    print(f"Pod outer:    {OUT_L:.1f} x {OUT_W:.1f} x {OUT_H:.1f} mm")
    print(f"Cavity:       {INNER_L:.1f} x {INNER_W:.1f} x {INNER_H:.1f} mm")
    for label, s in (("With sleeve:", sleeve), ("Loop sleeve:", sleeve_loop)):
        bb = s.val().BoundingBox()
        print(f"{label:13} {bb.xlen:.1f} x {bb.ylen:.1f} x {bb.zlen:.1f} mm")


if __name__ == "__main__":
    main()
