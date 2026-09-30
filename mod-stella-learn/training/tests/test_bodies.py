"""The body of a bot from its seed, against the vectors that GymBodyGameTest checks on the Java side."""

from stella_train.bodies import TOUGHNESS_BUCKETS, body_for_seed

VECTORS = [(1, 50, 20, 5, 2), (42, 26, 5, 4, 0), (1_000_000, 53, 13, 7, 4), (4_294_967_295, 35, 19, 0, 3), (777, 46, 6, 6, 0)]


def test_bodies_match_the_java_vectors():
    for seed, health, armor, toughness, protection in VECTORS:
        body = body_for_seed(seed)
        assert (body.max_health, body.armor, body.toughness, body.protection) == (health, armor, toughness, protection)


def test_ranges_and_special_share():
    specials = 0
    for seed in range(2000):
        body = body_for_seed(seed)
        assert 20 <= body.max_health <= 60 and 0 <= body.armor <= 20 and 0 <= body.toughness <= 12 and 0 <= body.protection <= 4
        assert 0 <= body.toughness_bucket < len(TOUGHNESS_BUCKETS)
        specials += body.special != "none"
    assert 140 < specials < 260
