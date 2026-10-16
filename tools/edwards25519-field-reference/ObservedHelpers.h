/*
	Public domain by Andrew M. <liquidsun@gmail.com>
	See: https://github.com/floodyberry/curve25519-donna

	Curve25519 implementation agnostic helpers
*/

/*
 * In:  b =   2^5 - 2^0
 * Out: b = 2^250 - 2^0
 */
static void
curve25519_pow_two5mtwo0_two250mtwo0(bignum25519 b) {
	bignum25519 ALIGN(16) t0,c;

	/* 2^5  - 2^0 */ /* b */
	/* 2^10 - 2^5 */ curve25519_square_times(t0, b, 5); ef_snapshot("square5:t0",t0);
	/* 2^10 - 2^0 */ curve25519_mul_noinline(b, t0, b); ef_snapshot("mul:b",b);
	/* 2^20 - 2^10 */ curve25519_square_times(t0, b, 10); ef_snapshot("square10:t0",t0);
	/* 2^20 - 2^0 */ curve25519_mul_noinline(c, t0, b); ef_snapshot("mul:c",c);
	/* 2^40 - 2^20 */ curve25519_square_times(t0, c, 20); ef_snapshot("square20:t0",t0);
	/* 2^40 - 2^0 */ curve25519_mul_noinline(t0, t0, c); ef_snapshot("mul:t0",t0);
	/* 2^50 - 2^10 */ curve25519_square_times(t0, t0, 10); ef_snapshot("square10:t0",t0);
	/* 2^50 - 2^0 */ curve25519_mul_noinline(b, t0, b); ef_snapshot("mul:b",b);
	/* 2^100 - 2^50 */ curve25519_square_times(t0, b, 50); ef_snapshot("square50:t0",t0);
	/* 2^100 - 2^0 */ curve25519_mul_noinline(c, t0, b); ef_snapshot("mul:c",c);
	/* 2^200 - 2^100 */ curve25519_square_times(t0, c, 100); ef_snapshot("square100:t0",t0);
	/* 2^200 - 2^0 */ curve25519_mul_noinline(t0, t0, c); ef_snapshot("mul:t0",t0);
	/* 2^250 - 2^50 */ curve25519_square_times(t0, t0, 50); ef_snapshot("square50:t0",t0);
	/* 2^250 - 2^0 */ curve25519_mul_noinline(b, t0, b); ef_snapshot("mul:b",b);
}

/*
 * z^(p - 2) = z(2^255 - 21)
 */
static void
curve25519_recip(bignum25519 out, const bignum25519 z) {
	bignum25519 ALIGN(16) a,t0,b;

	/* 2 */ curve25519_square_times(a, z, 1); ef_snapshot("square1:a",a); /* a = 2 */
	/* 8 */ curve25519_square_times(t0, a, 2); ef_snapshot("square2:t0",t0);
	/* 9 */ curve25519_mul_noinline(b, t0, z); ef_snapshot("mul:b",b); /* b = 9 */
	/* 11 */ curve25519_mul_noinline(a, b, a); ef_snapshot("mul:a",a); /* a = 11 */
	/* 22 */ curve25519_square_times(t0, a, 1); ef_snapshot("square1:t0",t0);
	/* 2^5 - 2^0 = 31 */ curve25519_mul_noinline(b, t0, b); ef_snapshot("mul:b",b);
	/* 2^250 - 2^0 */ curve25519_pow_two5mtwo0_two250mtwo0(b);
	/* 2^255 - 2^5 */ curve25519_square_times(b, b, 5); ef_snapshot("square5:b",b);
	/* 2^255 - 21 */ curve25519_mul_noinline(out, b, a); ef_snapshot("mul:out",out);
}

/*
 * z^((p-5)/8) = z^(2^252 - 3)
 */
static void
curve25519_pow_two252m3(bignum25519 two252m3, const bignum25519 z) {
	bignum25519 ALIGN(16) b,c,t0;

	/* 2 */ curve25519_square_times(c, z, 1); ef_snapshot("square1:c",c); /* c = 2 */
	/* 8 */ curve25519_square_times(t0, c, 2); ef_snapshot("square2:t0",t0); /* t0 = 8 */
	/* 9 */ curve25519_mul_noinline(b, t0, z); ef_snapshot("mul:b",b); /* b = 9 */
	/* 11 */ curve25519_mul_noinline(c, b, c); ef_snapshot("mul:c",c); /* c = 11 */
	/* 22 */ curve25519_square_times(t0, c, 1); ef_snapshot("square1:t0",t0);
	/* 2^5 - 2^0 = 31 */ curve25519_mul_noinline(b, t0, b); ef_snapshot("mul:b",b);
	/* 2^250 - 2^0 */ curve25519_pow_two5mtwo0_two250mtwo0(b);
	/* 2^252 - 2^2 */ curve25519_square_times(b, b, 2); ef_snapshot("square2:b",b);
	/* 2^252 - 3 */ curve25519_mul_noinline(two252m3, b, z); ef_snapshot("mul:two252m3",two252m3);
}
