#include <stdint.h>
extern void ripemd_trace(int,uint32_t,uint32_t,uint32_t,uint32_t,uint32_t,uint32_t,uint32_t,uint32_t,uint32_t,uint32_t);
/*
 * Copyright (C) 2006-2009 Vincent Hanquez <vincent@snarc.org>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include "crypton_ripemd.h"
#include "crypton_bitfn.h"
#include "crypton_align.h"
#include <string.h>

void observed_crypton_ripemd160_init(struct ripemd160_ctx *ctx)
{
	memset(ctx, 0, sizeof(*ctx));

	ctx->h[0] = 0x67452301;
	ctx->h[1] = 0xefcdab89;
	ctx->h[2] = 0x98badcfe;
	ctx->h[3] = 0x10325476;
	ctx->h[4] = 0xc3d2e1f0;
}

#define K1  0x00000000
#define K2  0x5a827999
#define K3  0x6ed9eba1
#define K4  0x8f1bbcdc
#define K5  0xa953fd4e
#define K6  0x50a28be6
#define K7  0x5c4dd124
#define K8  0x6d703ef3
#define K9  0x7a6d76e9

#define f1(x, y, z) (x ^ y ^ z)
#define f2(x, y, z) (z ^ (x & (y ^ z)))
#define f3(x, y, z) ((x | ~y) ^ z)
#define f4(x, y, z) (y ^ (z & (x ^ y)))
#define f5(x, y, z) (x ^ (y | ~z))

#define R(a, b, c, d, e, f, k, i, s)	\
	a += f(b, c, d) + w[i] + k; a = rol32(a, s) + e; c = rol32(c, 10)

static void observed_ripemd160_do_chunk(struct ripemd160_ctx *ctx, uint32_t *buf)
{
	uint32_t a1, b1, c1, d1, e1, a2, b2, c2, d2, e2;
#ifdef ARCH_IS_BIG_ENDIAN
	uint32_t w[16];
	cpu_to_le32_array(w, buf, 16);
#else
	uint32_t *w = buf;
#endif

	a1 = ctx->h[0]; b1 = ctx->h[1]; c1 = ctx->h[2]; d1 = ctx->h[3]; e1 = ctx->h[4];
	a2 = ctx->h[0]; b2 = ctx->h[1]; c2 = ctx->h[2]; d2 = ctx->h[3]; e2 = ctx->h[4];

	/* 5 passes on first state copy */
	R(a1, b1, c1, d1, e1, f1, K1, 0, 11);
 ripemd_trace(0,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f1, K1, 1, 14);
 ripemd_trace(1,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f1, K1, 2, 15);
 ripemd_trace(2,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f1, K1, 3, 12);
 ripemd_trace(3,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f1, K1, 4, 5);
 ripemd_trace(4,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f1, K1, 5, 8);
 ripemd_trace(5,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f1, K1, 6, 7);
 ripemd_trace(6,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f1, K1, 7, 9);
 ripemd_trace(7,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f1, K1, 8, 11);
 ripemd_trace(8,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f1, K1, 9, 13);
 ripemd_trace(9,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f1, K1, 10, 14);
 ripemd_trace(10,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f1, K1, 11, 15);
 ripemd_trace(11,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f1, K1, 12, 6);
 ripemd_trace(12,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f1, K1, 13, 7);
 ripemd_trace(13,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f1, K1, 14, 9);
 ripemd_trace(14,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f1, K1, 15, 8);
 ripemd_trace(15,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	
	R(e1, a1, b1, c1, d1, f2, K2, 7, 7);
 ripemd_trace(16,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f2, K2, 4, 6);
 ripemd_trace(17,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f2, K2, 13, 8);
 ripemd_trace(18,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f2, K2, 1, 13);
 ripemd_trace(19,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f2, K2, 10, 11);
 ripemd_trace(20,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f2, K2, 6, 9);
 ripemd_trace(21,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f2, K2, 15, 7);
 ripemd_trace(22,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f2, K2, 3, 15);
 ripemd_trace(23,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f2, K2, 12, 7);
 ripemd_trace(24,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f2, K2, 0, 12);
 ripemd_trace(25,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f2, K2, 9, 15);
 ripemd_trace(26,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f2, K2, 5, 9);
 ripemd_trace(27,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f2, K2, 2, 11);
 ripemd_trace(28,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f2, K2, 14, 7);
 ripemd_trace(29,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f2, K2, 11, 13);
 ripemd_trace(30,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f2, K2, 8, 12);
 ripemd_trace(31,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);

	R(d1, e1, a1, b1, c1, f3, K3, 3, 11);
 ripemd_trace(32,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f3, K3, 10, 13);
 ripemd_trace(33,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f3, K3, 14, 6);
 ripemd_trace(34,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f3, K3, 4, 7);
 ripemd_trace(35,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f3, K3, 9, 14);
 ripemd_trace(36,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f3, K3, 15, 9);
 ripemd_trace(37,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f3, K3, 8, 13);
 ripemd_trace(38,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f3, K3, 1, 15);
 ripemd_trace(39,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f3, K3, 2, 14);
 ripemd_trace(40,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f3, K3, 7, 8);
 ripemd_trace(41,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f3, K3, 0, 13);
 ripemd_trace(42,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f3, K3, 6, 6);
 ripemd_trace(43,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f3, K3, 13, 5);
 ripemd_trace(44,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f3, K3, 11, 12);
 ripemd_trace(45,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f3, K3, 5, 7);
 ripemd_trace(46,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f3, K3, 12, 5);
 ripemd_trace(47,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);

	R(c1, d1, e1, a1, b1, f4, K4, 1, 11);
 ripemd_trace(48,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f4, K4, 9, 12);
 ripemd_trace(49,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f4, K4, 11, 14);
 ripemd_trace(50,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f4, K4, 10, 15);
 ripemd_trace(51,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f4, K4, 0, 14);
 ripemd_trace(52,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f4, K4, 8, 15);
 ripemd_trace(53,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f4, K4, 12, 9);
 ripemd_trace(54,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f4, K4, 4, 8);
 ripemd_trace(55,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f4, K4, 13, 9);
 ripemd_trace(56,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f4, K4, 3, 14);
 ripemd_trace(57,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f4, K4, 7, 5);
 ripemd_trace(58,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f4, K4, 15, 6);
 ripemd_trace(59,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f4, K4, 14, 8);
 ripemd_trace(60,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f4, K4, 5, 6);
 ripemd_trace(61,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f4, K4, 6, 5);
 ripemd_trace(62,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f4, K4, 2, 12);
 ripemd_trace(63,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);

	R(b1, c1, d1, e1, a1, f5, K5, 4, 9);
 ripemd_trace(64,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f5, K5, 0, 15);
 ripemd_trace(65,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f5, K5, 5, 5);
 ripemd_trace(66,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f5, K5, 9, 11);
 ripemd_trace(67,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f5, K5, 7, 6);
 ripemd_trace(68,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f5, K5, 12, 8);
 ripemd_trace(69,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f5, K5, 2, 13);
 ripemd_trace(70,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f5, K5, 10, 12);
 ripemd_trace(71,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f5, K5, 14, 5);
 ripemd_trace(72,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f5, K5, 1, 12);
 ripemd_trace(73,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f5, K5, 3, 13);
 ripemd_trace(74,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a1, b1, c1, d1, e1, f5, K5, 8, 14);
 ripemd_trace(75,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e1, a1, b1, c1, d1, f5, K5, 11, 11);
 ripemd_trace(76,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d1, e1, a1, b1, c1, f5, K5, 6, 8);
 ripemd_trace(77,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c1, d1, e1, a1, b1, f5, K5, 15, 5);
 ripemd_trace(78,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b1, c1, d1, e1, a1, f5, K5, 13, 6);
 ripemd_trace(79,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);

	/* 5 passes on second state copy */
	R(a2, b2, c2, d2, e2, f5, K6, 5, 8);
 ripemd_trace(80,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f5, K6, 14, 9);
 ripemd_trace(81,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f5, K6, 7, 9);
 ripemd_trace(82,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f5, K6, 0, 11);
 ripemd_trace(83,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f5, K6, 9, 13);
 ripemd_trace(84,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f5, K6, 2, 15);
 ripemd_trace(85,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f5, K6, 11, 15);
 ripemd_trace(86,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f5, K6, 4, 5);
 ripemd_trace(87,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f5, K6, 13, 7);
 ripemd_trace(88,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f5, K6, 6, 7);
 ripemd_trace(89,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f5, K6, 15, 8);
 ripemd_trace(90,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f5, K6, 8, 11);
 ripemd_trace(91,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f5, K6, 1, 14);
 ripemd_trace(92,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f5, K6, 10, 14);
 ripemd_trace(93,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f5, K6, 3, 12);
 ripemd_trace(94,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f5, K6, 12, 6);
 ripemd_trace(95,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);

	R(e2, a2, b2, c2, d2, f4, K7, 6, 9);
 ripemd_trace(96,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f4, K7, 11, 13);
 ripemd_trace(97,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f4, K7, 3, 15);
 ripemd_trace(98,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f4, K7, 7, 7);
 ripemd_trace(99,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f4, K7, 0, 12);
 ripemd_trace(100,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f4, K7, 13, 8);
 ripemd_trace(101,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f4, K7, 5, 9);
 ripemd_trace(102,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f4, K7, 10, 11);
 ripemd_trace(103,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f4, K7, 14, 7);
 ripemd_trace(104,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f4, K7, 15, 7);
 ripemd_trace(105,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f4, K7, 8, 12);
 ripemd_trace(106,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f4, K7, 12, 7);
 ripemd_trace(107,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f4, K7, 4, 6);
 ripemd_trace(108,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f4, K7, 9, 15);
 ripemd_trace(109,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f4, K7, 1, 13);
 ripemd_trace(110,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f4, K7, 2, 11);
 ripemd_trace(111,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);

	R(d2, e2, a2, b2, c2, f3, K8, 15, 9);
 ripemd_trace(112,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f3, K8, 5, 7);
 ripemd_trace(113,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f3, K8, 1, 15);
 ripemd_trace(114,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f3, K8, 3, 11);
 ripemd_trace(115,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f3, K8, 7, 8);
 ripemd_trace(116,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f3, K8, 14, 6);
 ripemd_trace(117,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f3, K8, 6, 6);
 ripemd_trace(118,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f3, K8, 9, 14);
 ripemd_trace(119,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f3, K8, 11, 12);
 ripemd_trace(120,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f3, K8, 8, 13);
 ripemd_trace(121,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f3, K8, 12, 5);
 ripemd_trace(122,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f3, K8, 2, 14);
 ripemd_trace(123,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f3, K8, 10, 13);
 ripemd_trace(124,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f3, K8, 0, 13);
 ripemd_trace(125,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f3, K8, 4, 7);
 ripemd_trace(126,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f3, K8, 13, 5);
 ripemd_trace(127,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);

	R(c2, d2, e2, a2, b2, f2, K9, 8, 15);
 ripemd_trace(128,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f2, K9, 6, 5);
 ripemd_trace(129,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f2, K9, 4, 8);
 ripemd_trace(130,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f2, K9, 1, 11);
 ripemd_trace(131,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f2, K9, 3, 14);
 ripemd_trace(132,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f2, K9, 11, 14);
 ripemd_trace(133,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f2, K9, 15, 6);
 ripemd_trace(134,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f2, K9, 0, 14);
 ripemd_trace(135,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f2, K9, 5, 6);
 ripemd_trace(136,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f2, K9, 12, 9);
 ripemd_trace(137,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f2, K9, 2, 12);
 ripemd_trace(138,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f2, K9, 13, 9);
 ripemd_trace(139,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f2, K9, 9, 12);
 ripemd_trace(140,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f2, K9, 7, 5);
 ripemd_trace(141,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f2, K9, 10, 15);
 ripemd_trace(142,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f2, K9, 14, 8);
 ripemd_trace(143,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);

	R(b2, c2, d2, e2, a2, f1, K1, 12, 8);
 ripemd_trace(144,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f1, K1, 15, 5);
 ripemd_trace(145,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f1, K1, 10, 12);
 ripemd_trace(146,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f1, K1, 4, 9);
 ripemd_trace(147,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f1, K1, 1, 12);
 ripemd_trace(148,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f1, K1, 5, 5);
 ripemd_trace(149,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f1, K1, 8, 14);
 ripemd_trace(150,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f1, K1, 7, 6);
 ripemd_trace(151,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f1, K1, 6, 8);
 ripemd_trace(152,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f1, K1, 2, 13);
 ripemd_trace(153,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f1, K1, 13, 6);
 ripemd_trace(154,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(a2, b2, c2, d2, e2, f1, K1, 14, 5);
 ripemd_trace(155,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(e2, a2, b2, c2, d2, f1, K1, 0, 15);
 ripemd_trace(156,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(d2, e2, a2, b2, c2, f1, K1, 3, 13);
 ripemd_trace(157,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(c2, d2, e2, a2, b2, f1, K1, 9, 11);
 ripemd_trace(158,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);
	R(b2, c2, d2, e2, a2, f1, K1, 11, 11);
 ripemd_trace(159,a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);

	d2 += c1 + ctx->h[1];
	ctx->h[1] = ctx->h[2] + d1 + e2;
	ctx->h[2] = ctx->h[3] + e1 + a2;
	ctx->h[3] = ctx->h[4] + a1 + b2;
	ctx->h[4] = ctx->h[0] + b1 + c2;
	ctx->h[0] = d2;
}

void observed_crypton_ripemd160_update(struct ripemd160_ctx *ctx, const uint8_t *data, uint32_t len)
{
	uint32_t index, to_fill;

	index = (uint32_t) (ctx->sz & 0x3f);
	to_fill = 64 - index;

	ctx->sz += len;
	if (index && len >= to_fill) {
		memcpy(ctx->buf + index, data, to_fill);
		observed_ripemd160_do_chunk(ctx, (uint32_t *) ctx->buf);
		len -= to_fill;
		data += to_fill;
		index = 0;
	}

	if (need_alignment(data, 4)) {
		uint32_t tramp[16];
		ASSERT_ALIGNMENT(tramp, 4);
		for (; len >= 64; len -= 64, data += 64) {
			memcpy(tramp, data, 64);
			observed_ripemd160_do_chunk(ctx, tramp);
		}
	} else {
		/* process as much 64-block as possible */
		for (; len >= 64; len -= 64, data += 64)
			observed_ripemd160_do_chunk(ctx, (uint32_t *) data);
	}

	/* append data into buf */
	if (len)
		memcpy(ctx->buf + index, data, len);
}

void observed_crypton_ripemd160_finalize(struct ripemd160_ctx *ctx, uint8_t *out)
{
	static uint8_t padding[64] = { 0x80, };
	uint64_t bits;
	uint32_t index, padlen;

	/* add padding and update data with it */
	bits = cpu_to_le64(ctx->sz << 3);

	/* pad out to 56 */
	index = (uint32_t) (ctx->sz & 0x3f);
	padlen = (index < 56) ? (56 - index) : ((64 + 56) - index);
	observed_crypton_ripemd160_update(ctx, padding, padlen);

	/* append length */
	observed_crypton_ripemd160_update(ctx, (uint8_t *) &bits, sizeof(bits));

	/* output digest */
	store_le32(out   , ctx->h[0]);
	store_le32(out+ 4, ctx->h[1]);
	store_le32(out+ 8, ctx->h[2]);
	store_le32(out+12, ctx->h[3]);
	store_le32(out+16, ctx->h[4]);
}
