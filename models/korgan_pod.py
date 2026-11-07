"""
Korgan pod — compact wrist enclosure (Fitbit-Air-style pebble + TPU sleeve).

Generates (next to this script):
    pod_case.step   (+ STL/pod_case.stl)        - body, PETG/PLA
    pod_lid.step    (+ STL/pod_lid.stl)         - lid with button / LED / switch / buzzer grille
    pod_sleeve.step (+ STL/pod_sleeve.stl)      - TPU 95A sleeve with lugs for 20 mm spring bars
    pod_sleeve_loop.step (+ STL/pod_sleeve_loop.stl) - same sleeve with slots for a 25 mm velcro loop strap
    pod_assembly.step       - everything together incl. dummy components

Run:  python models/korgan_pod.py      (needs: pip install cadquery)

Layout (X = along the strap, Y = across, Z = up, 0 = top of floor):

    top layer   | ESP32-C3 SuperMini | TP4056 charger | UI bay: button, LED, switch |
    bottom      |        battery 30x15x5       |rib|      MPU6050 on 2 screw bosses   |

Both USB-C ports (ESP + charger) come out of the same long side (Y = 0).
All sizes are parameters below - measure your boards with a caliper and adjust.
"""
from pathlib import Path

import cadquery as cq

OUT = Path(__file__).resolve().parent
STL_DIR = OUT / "STL"            # print-ready meshes

# ---------------------------------------------------------------- tolerances
TOL = 0.3            # gap around every board / battery
WALL = 1.6
FLOOR = 1.2
LID_T = 1.2
REBATE = 0.8         # lid sits in a step this wide at the top of the wall
LID_GAP = 0.12       # lid-to-rebate clearance per side
R_OUT = 5.0          # vertical corner radius of the pebble
R_IN = 1.0           # cavity corner radius (small, so boards reach the corners)

# ---------------------------------------------------------------- components
BATT = (30.0, 15.0, 5.0)              # L x W x H
ESP = (18.0, 22.52, 1.0)              # placed rotated: 18 along X, USB-C on Y=0 edge
ESP_USB_H = 3.3                       # USB-C receptacle height above the PCB
CHG = (20.0, 25.0, 1.0)               # TP4056 Type-C, rotated: USB-C on Y=0 edge
MPU = (16.0, 20.0, 1.6)               # GY-521, 16 along X, header edge toward battery
MPU_PARTS_H = 1.2                     # tallest part on the GY-521
MPU_HOLE_SPACING = 15.24              # centre-to-centre of the two M2/M3 holes
MPU_HOLE_EDGE = 2.5                   # hole centre from the board edge
# Heat-set brass inserts for the GY-521 screws. Measure yours with a caliper.
INSERT_OD = 3.2                       # outer diameter over the knurl
INSERT_L = 3.0                        # insert length
INSERT_HOLE = INSERT_OD - 0.2         # hole is a bit smaller: the knurl melts into the walls
INSERT_SPARE = 0.2                    # extra depth for plastic pushed down by the insert
BOTTOM_SKIN = 0.4                     # plastic left under the hole (bottom stays closed)
BOSS_OD = INSERT_OD + 2 * 1.2         # 1.2 mm wall around the insert
# boss height follows from the insert: hole depth = insert + spare, floor helps from below
BOSS_H = INSERT_L + INSERT_SPARE - (FLOOR - BOTTOM_SKIN)

BOARD_Z = BATT[2] + 0.4               # boards lie on the battery (+0.4 foam tape)
assert BOSS_H + MPU[2] + MPU_PARTS_H <= BOARD_Z - 0.1, (
    f"GY-521 on {BOSS_H:.1f} mm bosses would hit the charger above it: "
    "use a shorter insert (INSERT_L) or a taller case")
# Extra room added to the tightest possible pod, per dimension (mm).
# Length goes to the end with the button bay, width to the side opposite the USB-C ports,
# height above the boards.
EXTRA_L = 0.0
EXTRA_W = 0.0
EXTRA_H = 0.0

INNER_H = BOARD_Z + ESP[2] + ESP_USB_H + 0.3 + EXTRA_H

TAB_SPACE = 1.4                       # free strip at both X ends for lid snap tabs

# X positions (inside the cavity)
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

# UI bay = free top space above the MPU, right of the charger
BAY_X0 = CHG_X0 + CHG[0] + TOL
BAY_CX = (BAY_X0 + INNER_L - TAB_SPACE) / 2

# USB-C cut-outs (stadium) in the Y=0 wall
USB_W, USB_H = 9.6, 3.8
USB_Z = BOARD_Z + ESP[2] + ESP_USB_H / 2
ESP_USB_X = ESP_X0 + ESP[0] / 2
CHG_USB_X = CHG_X0 + CHG[0] / 2

# Lid features (same XY as the cavity)
# Button, LED and switch sit in one row across the bay, ~3 mm apart (hole edge to hole edge).
BTN_HOLE = 5.5                 # square hole for the plunger / button cap
BTN_SOCKET = 7.0               # square socket under the lid for the 6x6 tactile body (+ glue)
BTN_SOCKET_DEPTH = 3.4
UI_X = BAY_CX + 0.3            # row of controls, nudged away from the charger edge
BTN_POS = (UI_X, 5.0)
LED_D = 5.0                    # 5 mm LED (3 mm also fits: set 3.1)
LED_POS = (UI_X, 13.3)
SW_SIZE = (8.0, 3.0)           # switch measured with a caliper (X, Y)
SW_SLOT = (SW_SIZE[0] + 0.4, SW_SIZE[1] + 0.4)  # rectangular hole, 0.2 mm gap per side
SW_POS = (UI_X, 20.8)
BUZZ_D = 12.0                  # 12 mm piezo disc glued into a recess under the grille
BUZZ_RECESS = 0.4
BUZZ_POS = (ESP_X0 + ESP[0] / 2, 14.0)

SNAP_W, SNAP_DEPTH, SNAP_T, SNAP_BUMP = 6.0, 2.6, 0.8, 0.35

# Sleeve (TPU 95A)
SLV_WALL = 1.4
SLV_BOTTOM = 1.0
# The pod must stay in the sleeve when the wearer falls. The printed case is not
# changed for this: the sleeve alone holds it with a thick lip all around, a snug fit and
#   retain="bridge":  a strap across the plain middle of the lid, printed on a tear-off
#                     support (pod_sleeve*.stl), or
#   retain="wide":    no strap, one even, wider lip all around (pod_sleeve*_nobridge.stl).
SLV_LIP = 2.0                  # how far the lip covers the lid edge (lid holes stay open)
SLV_LIP_T = 1.2                # lip thickness above the lid (thin TPU flips over and lets the pod out)
SLV_FIT = 0.05                 # snug: TPU stretches over the pod and grips it
BRIDGE_W = 8.0                 # strap width along the pod
SUPPORT_GAP = 0.4              # air gap between the tear-off support and the strap (2 layers of 0.2)
SUPPORT_T = 1.2                # support wall thickness
SLV_LIP_WIDE = 2.6             # "wide" variant: even lip all around (wider would cover the button)
SW_NOTCH_MARGIN = 1.5          # lip notch around the switch lever, extra width on each side
LUG_GAP = 20.0                 # strap width = distance between the lugs
LUG_T = 3.0                    # lug thickness (TPU is soft, so thicker than on a watch)
LUG_LEN = 5.2                  # how far the lugs stick out of the sleeve
LUG_ROOT = 2.6                 # how deep the lugs reach into the sleeve wall
LUG_H = 5.6
BAR_HOLE = 1.1                 # spring bar tip is 0.8-1.0 mm
BAR_HOLE_DEPTH = 1.8           # blind, leaves 1.2 mm of TPU outside
BAR_OFFSET = 2.7               # bar centre from the sleeve end
BAR_Z = 2.8                    # bar centre above the sleeve bottom
# pod_sleeve_loop: slots for an elastic velcro loop strap (e.g. 25 mm cable-tie style)
SLOT_W = 26.5                  # strap width + 1.5
SLOT_T = 3.2                   # strap thickness + ~1
SLOT_SIDE = 2.4                # material left on each side of the slot
SLOT_BAR = 2.6                 # width of the bar the strap wraps around
SLOT_LEN = 6.6                 # how far the bar sticks out of the sleeve
SLOT_BAR_T = 3.0


def rounded_box(l, w, h, r, x0=0.0, y0=0.0, z0=0.0):
    return (cq.Workplane("XY").workplane(offset=z0)
            .center(x0 + l / 2, y0 + w / 2)
            .rect(l, w).extrude(h)
            .edges("|Z").fillet(r))


def stadium_y_wall(cx, cz, w, h, y0, depth):
    """Slot through a wall normal to Y, centred at (cx, cz)."""
    return (cq.Workplane("XZ", origin=(0, y0 + depth, 0))
            .center(cx, cz).slot2D(w, h).extrude(depth + 0.2))


def make_case():
    body = rounded_box(OUT_L, OUT_W, OUT_H, R_OUT, -WALL, -WALL, -FLOOR)
    body = body.faces("<Z").edges().fillet(2.0).faces(">Z").edges().fillet(0.6)
    body = body.cut(rounded_box(INNER_L, INNER_W, INNER_H + LID_T + 1, R_IN))
    # step at the top of the wall the lid drops into
    body = body.cut(rounded_box(INNER_L + 2 * REBATE, INNER_W + 2 * REBATE, LID_T + 1,
                                R_IN + REBATE, -REBATE, -REBATE, INNER_H))

    # MPU6050 bosses with holes for heat-set inserts (hole side toward the end wall)
    hx = MPU_X0 + MPU[0] - MPU_HOLE_EDGE
    for dy in (-MPU_HOLE_SPACING / 2, MPU_HOLE_SPACING / 2):
        boss = (cq.Workplane("XY").center(hx, INNER_W / 2 + dy)
                .circle(BOSS_OD / 2).extrude(BOSS_H))
        body = body.union(boss)
        body = body.cut(cq.Workplane("XY").workplane(offset=-FLOOR + BOTTOM_SKIN)
                        .center(hx, INNER_W / 2 + dy).circle(INSERT_HOLE / 2)
                        .extrude(BOSS_H + FLOOR))

    # USB-C ports
    for ux in (ESP_USB_X, CHG_USB_X):
        body = body.cut(stadium_y_wall(ux, USB_Z, USB_W, USB_H, -WALL - 0.1, WALL + 0.3))

    # snap grooves for the lid tabs (both short ends)
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

    # button: plunger hole + socket that holds the 6x6 tactile body
    sock = (cq.Workplane("XY").workplane(offset=INNER_H - BTN_SOCKET_DEPTH)
            .center(*BTN_POS).rect(BTN_SOCKET + 1.6, BTN_SOCKET + 1.6)
            .extrude(BTN_SOCKET_DEPTH))
    lid = lid.union(sock)
    lid = lid.cut(cq.Workplane("XY").workplane(offset=INNER_H - BTN_SOCKET_DEPTH - 0.1)
                  .center(*BTN_POS).rect(BTN_SOCKET, BTN_SOCKET)
                  .extrude(BTN_SOCKET_DEPTH + 0.1))
    lid = lid.cut(cq.Workplane("XY").workplane(offset=INNER_H - 0.1)
                  .center(*BTN_POS).rect(BTN_HOLE, BTN_HOLE).extrude(LID_T + 0.2))
    # LED
    lid = lid.cut(cq.Workplane("XY").workplane(offset=INNER_H - 0.1)
                  .center(*LED_POS).circle(LED_D / 2).extrude(LID_T + 0.2))
    # slide switch: rectangular hole
    lid = lid.cut(cq.Workplane("XY").workplane(offset=INNER_H - 0.1)
                  .center(*SW_POS).rect(*SW_SLOT)
                  .extrude(LID_T + 0.2))
    # buzzer: recess for a 12 mm piezo disc + sound grille
    lid = lid.cut(cq.Workplane("XY").workplane(offset=INNER_H - 0.1)
                  .center(*BUZZ_POS).circle(BUZZ_D / 2 + 0.2).extrude(BUZZ_RECESS + 0.1))
    grille = [(0, 0)] + [(3.2 * c, 3.2 * s) for c, s in
                         ((1, 0), (0.5, 0.866), (-0.5, 0.866), (-1, 0), (-0.5, -0.866), (0.5, -0.866))]
    lid = lid.cut(cq.Workplane("XY").workplane(offset=INNER_H)
                  .pushPoints([(BUZZ_POS[0] + x, BUZZ_POS[1] + y) for x, y in grille])
                  .circle(0.9).extrude(LID_T + 0.2))

    # snap tabs on both short ends
    for x, sgn in ((0.1, -1), (INNER_L - 0.1 - SNAP_T, 1)):
        tab = (cq.Workplane("XY").box(SNAP_T, SNAP_W, SNAP_DEPTH, centered=False)
               .translate((x, INNER_W / 2 - SNAP_W / 2, INNER_H - SNAP_DEPTH)))
        bump = (cq.Workplane("XY").box(SNAP_BUMP + 0.01, SNAP_W, 0.8, centered=False)
                .translate((x - SNAP_BUMP if sgn < 0 else x + SNAP_T,
                            INNER_W / 2 - SNAP_W / 2, INNER_H - SNAP_DEPTH + 0.5)))
        lid = lid.union(tab).union(bump)
    return lid


def make_sleeve(strap="lugs", retain="bridge"):
    """strap="lugs": spring-bar lugs 20 mm; strap="loop": slots for a 25 mm velcro loop.
    retain="bridge": strap across the lid; retain="wide": no strap, wider even lip."""
    il, iw = OUT_L + 2 * SLV_FIT, OUT_W + 2 * SLV_FIT
    x0, y0 = -WALL - SLV_FIT, -WALL - SLV_FIT
    h = SLV_BOTTOM + OUT_H + 0.1 + SLV_LIP_T
    zb = -FLOOR - SLV_BOTTOM
    shell = rounded_box(il + 2 * SLV_WALL, iw + 2 * SLV_WALL, h, R_OUT + SLV_WALL + SLV_FIT,
                        x0 - SLV_WALL, y0 - SLV_WALL, zb)
    shell = shell.faces("<Z").edges().fillet(2.2).faces(">Z").edges().fillet(0.6)
    # pod pocket
    shell = shell.cut(rounded_box(il, iw, OUT_H + 0.1, R_OUT + SLV_FIT, x0, y0, -FLOOR))
    # top window: a plain rounded rectangle, the lip is the same width all around
    lip = SLV_LIP_WIDE if retain == "wide" else SLV_LIP
    shell = shell.cut(rounded_box(il - 2 * lip, iw - 2 * lip, 5,
                                  R_OUT + SLV_FIT - lip, x0 + lip, y0 + lip, OUT_H - 2))
    pod_top = OUT_H - FLOOR
    # lip extras are clipped to the pocket (+0.3 mm into the wall to fuse): nothing may poke
    # through the rounded outside or the rounded top edge of the sleeve
    pocket_plus = rounded_box(il + 0.6, iw + 0.6, h + 2, R_OUT + SLV_FIT + 0.3, x0 - 0.3, y0 - 0.3, zb - 1)
    # notch in the end lip so the finger reaches the switch lever at the end of its travel
    # (only the lip over the lid is removed; the sleeve wall stays whole)
    nx0 = SW_POS[0] + SW_SLOT[0] / 2 - 0.5
    nx1 = x0 + il                                      # inner face of the end wall
    ny0 = SW_POS[1] - SW_SLOT[1] / 2 - SW_NOTCH_MARGIN
    ny1 = SW_POS[1] + SW_SLOT[1] / 2 + SW_NOTCH_MARGIN
    shell = shell.cut(cq.Workplane("XY").box(nx1 - nx0, ny1 - ny0, SLV_LIP_T + 2, centered=False)
                      .translate((nx0, ny0, pod_top + 0.05)))
    # strap across the lid: the pod cannot pop out through the top window
    if retain == "bridge":
        bx = BRIDGE_X
        band = (cq.Workplane("XY")
                .box(BRIDGE_W, iw + 2 * SLV_WALL - 1.0, SLV_LIP_T)
                .edges("|X").fillet(0.5)
                .translate((bx, y0 + iw / 2, pod_top + 0.1 + SLV_LIP_T / 2)))
        shell = shell.union(band.intersect(pocket_plus))
    # skin-side window keeps it thin and breathable
    shell = shell.cut(rounded_box(il - 12, iw - 10, 3, 4, x0 + 6, y0 + 5, zb - 1))
    # USB-C access so the pod charges / flashes without taking it out
    for ux in (ESP_USB_X, CHG_USB_X):
        shell = shell.cut(stadium_y_wall(ux, USB_Z, 12.5, 7.0, y0 - SLV_WALL - 0.2, SLV_WALL + 0.6))

    cy = y0 + iw / 2
    for sgn in (-1, 1):
        xe = x0 - SLV_WALL if sgn < 0 else x0 + il + SLV_WALL
        if strap == "loop":
            # flat bar with a vertical slot: the strap passes through it and under the pod
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
        # standard watch lugs: any 20 mm strap on spring bars fits
        bar_x = xe + sgn * BAR_OFFSET
        for side in (-1, 1):
            ly = cy + side * (LUG_GAP / 2 + LUG_T / 2)
            lug = (cq.Workplane("XY")
                   .box(LUG_LEN + LUG_ROOT, LUG_T, LUG_H)
                   .edges("|Y and %sX" % (">" if sgn > 0 else "<")).fillet(LUG_H / 2 - 0.3)
                   .translate((xe + sgn * (LUG_LEN - LUG_ROOT) / 2, ly, zb + LUG_H / 2)))
            # blind spring-bar hole from the inner face of the lug
            hole = (cq.Workplane("XZ", origin=(0, cy + side * LUG_GAP / 2, 0))
                    .center(bar_x, zb + BAR_Z).circle(BAR_HOLE / 2)
                    .extrude(side * -BAR_HOLE_DEPTH - side * 0.01))
            shell = shell.union(lug).cut(hole)
    # re-open the pocket where the lugs / bars overlapped it
    shell = shell.cut(rounded_box(il, iw, OUT_H + 0.1, R_OUT + SLV_FIT, x0, y0, -FLOOR))

    return shell


BRIDGE_X = (BUZZ_POS[0] + BUZZ_D / 2 + BAY_X0) / 2   # plain lid area between grille and bay


def make_bridge_support():
    """Tear-off support under the sleeve's strap: stands on the bed through the skin window,
    SUPPORT_GAP below the strap. Printed together with the sleeve, pulled out afterwards."""
    iw = OUT_W + 2 * SLV_FIT
    y0 = -WALL - SLV_FIT
    zb = -FLOOR - SLV_BOTTOM
    top = OUT_H - FLOOR + 0.1 - SUPPORT_GAP
    ya, yb = y0 + 5 + 0.6, y0 + iw - 5 - 0.6          # inside the skin window, not touching the sleeve
    xa, xb = BRIDGE_X - BRIDGE_W / 2 + 0.6, BRIDGE_X + BRIDGE_W / 2 - 0.6
    walls = None
    for x in (xa, BRIDGE_X, xb):                       # three walls along the strap ...
        w = cq.Workplane("XY").box(SUPPORT_T, yb - ya, top - zb, centered=False).translate((x - SUPPORT_T / 2, ya, zb))
        walls = w if walls is None else walls.union(w)
    for y in (ya, yb - SUPPORT_T):                     # ... tied together at both ends so they don't wobble
        walls = walls.union(cq.Workplane("XY").box(xb - xa + SUPPORT_T, SUPPORT_T, top - zb, centered=False)
                            .translate((xa - SUPPORT_T / 2, y, zb)))
    return walls


def dummies():
    """Simple blocks of the electronics for the assembly preview."""
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
        # sleeves with the strap get the tear-off support in the same STL, ready to print,
        # and a separate STEP with the support for viewing / editing in CAD
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
