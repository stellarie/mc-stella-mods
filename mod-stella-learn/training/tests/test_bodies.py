"""The body of a bot from its seed, against the vectors that GymBodyGameTest checks on the Java side."""

from stella_train.bodies import TOUGHNESS_BUCKETS, body_for_seed

VECTORS = [(1, 50, 20, 5, 2), (42, 26, 5, 4, 0), (1_000_000, 53, 13, 7, 4), (4_294_967_295, 35, 19, 0, 3), (777, 46, 6, 6, 0)]


def test_bodies_match_the_java_vectors():
    for seed, health, armor, toughness, protection in VECTORS:
        body = body_for_seed(seed)
        assert (body.max_health, body.armor, body.toughness, body.protection) == (health, armor, toughness, protection)


SPEEDS = [(1, 1.0185, 0.8284), (42, 1.2405, 0.8719), (1000000, 0.665, 1.2828), (777, 0.867, 1.1519)]


def test_speeds_match_the_java_vectors_and_stay_in_range():
    from stella_train.bodies import BodyRange

    wide = BodyRange(speed_min_permille=600, speed_max_permille=1400)
    for seed, archer, bot in SPEEDS:
        body = body_for_seed(seed, wide)
        assert (round(body.archer_speed, 4), round(body.bot_speed, 4)) == (archer, bot)
    assert body_for_seed(5).archer_speed == 1.0 and body_for_seed(5).bot_speed == 1.0


def test_ranges_and_special_share():
    specials = 0
    for seed in range(2000):
        body = body_for_seed(seed)
        assert 20 <= body.max_health <= 60 and 0 <= body.armor <= 20 and 0 <= body.toughness <= 12 and 0 <= body.protection <= 4
        assert 0 <= body.toughness_bucket < len(TOUGHNESS_BUCKETS)
        specials += body.special != "none"
    assert 140 < specials < 260


def test_stage_ranges_bound_the_draw_and_leave_the_full_vectors_alone():
    from stella_train import roster
    from stella_train.bodies import range_for_stage

    frail = range_for_stage(roster.STAGE_A)
    middle = range_for_stage(roster.STAGE_B)
    for seed in range(1000):
        a = body_for_seed(seed, frail)
        assert 20 <= a.max_health <= 26 and a.armor <= 4 and a.toughness == 0 and a.protection == 0 and a.special == "none"
        b = body_for_seed(seed, middle)
        assert 20 <= b.max_health <= 40 and b.armor <= 12 and b.toughness == 0 and b.protection <= 2 and b.special == "none"
    assert range_for_stage(roster.STAGE_RELEASE) == range_for_stage(roster.STAGE_C)
