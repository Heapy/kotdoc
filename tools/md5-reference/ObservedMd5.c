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

#include <string.h>
#include <stdio.h>
#include "crypton_bitfn.h"
#include "crypton_align.h"
#include "crypton_md5.h"

void crypton_md5_init(struct md5_ctx *ctx)
{
	memset(ctx, 0, sizeof(*ctx));

	ctx->sz = 0ULL;
	ctx->h[0] = 0x67452301;
	ctx->h[1] = 0xefcdab89;
	ctx->h[2] = 0x98badcfe;
	ctx->h[3] = 0x10325476;
}

#define f1(x, y, z)	(z ^ (x & (y ^ z)))
#define f2(x, y, z)	f1(z, x, y)
#define f3(x, y, z)	(x ^ y ^ z)
#define f4(x, y, z)	(y ^ (x | ~z))
#define R(f, a, b, c, d, i, k, s) a += f(b, c, d) + w[i] + k; a = rol32(a, s); a += b

static void md5_do_chunk(struct md5_ctx *ctx, uint32_t *buf)
{
	uint32_t a, b, c, d;
#ifdef ARCH_IS_BIG_ENDIAN
	uint32_t w[16];
	cpu_to_le32_array(w, buf, 16);
#else
	uint32_t *w = buf;
#endif
	a = ctx->h[0]; b = ctx->h[1]; c = ctx->h[2]; d = ctx->h[3];

	ref_snapshot(999,16,w);
	R(f1, a, b, c, d, 0, 0xd76aa478, 7); ref_snapshot(0,4,(uint32_t[]){d,a,b,c});
	R(f1, d, a, b, c, 1, 0xe8c7b756, 12); ref_snapshot(1,4,(uint32_t[]){c,d,a,b});
	R(f1, c, d, a, b, 2, 0x242070db, 17); ref_snapshot(2,4,(uint32_t[]){b,c,d,a});
	R(f1, b, c, d, a, 3, 0xc1bdceee, 22); ref_snapshot(3,4,(uint32_t[]){a,b,c,d});
	R(f1, a, b, c, d, 4, 0xf57c0faf, 7); ref_snapshot(4,4,(uint32_t[]){d,a,b,c});
	R(f1, d, a, b, c, 5, 0x4787c62a, 12); ref_snapshot(5,4,(uint32_t[]){c,d,a,b});
	R(f1, c, d, a, b, 6, 0xa8304613, 17); ref_snapshot(6,4,(uint32_t[]){b,c,d,a});
	R(f1, b, c, d, a, 7, 0xfd469501, 22); ref_snapshot(7,4,(uint32_t[]){a,b,c,d});
	R(f1, a, b, c, d, 8, 0x698098d8, 7); ref_snapshot(8,4,(uint32_t[]){d,a,b,c});
	R(f1, d, a, b, c, 9, 0x8b44f7af, 12); ref_snapshot(9,4,(uint32_t[]){c,d,a,b});
	R(f1, c, d, a, b, 10, 0xffff5bb1, 17); ref_snapshot(10,4,(uint32_t[]){b,c,d,a});
	R(f1, b, c, d, a, 11, 0x895cd7be, 22); ref_snapshot(11,4,(uint32_t[]){a,b,c,d});
	R(f1, a, b, c, d, 12, 0x6b901122, 7); ref_snapshot(12,4,(uint32_t[]){d,a,b,c});
	R(f1, d, a, b, c, 13, 0xfd987193, 12); ref_snapshot(13,4,(uint32_t[]){c,d,a,b});
	R(f1, c, d, a, b, 14, 0xa679438e, 17); ref_snapshot(14,4,(uint32_t[]){b,c,d,a});
	R(f1, b, c, d, a, 15, 0x49b40821, 22); ref_snapshot(15,4,(uint32_t[]){a,b,c,d});

	R(f2, a, b, c, d, 1, 0xf61e2562, 5); ref_snapshot(16,4,(uint32_t[]){d,a,b,c});
	R(f2, d, a, b, c, 6, 0xc040b340, 9); ref_snapshot(17,4,(uint32_t[]){c,d,a,b});
	R(f2, c, d, a, b, 11, 0x265e5a51, 14); ref_snapshot(18,4,(uint32_t[]){b,c,d,a});
	R(f2, b, c, d, a, 0, 0xe9b6c7aa, 20); ref_snapshot(19,4,(uint32_t[]){a,b,c,d});
	R(f2, a, b, c, d, 5, 0xd62f105d, 5); ref_snapshot(20,4,(uint32_t[]){d,a,b,c});
	R(f2, d, a, b, c, 10, 0x02441453, 9); ref_snapshot(21,4,(uint32_t[]){c,d,a,b});
	R(f2, c, d, a, b, 15, 0xd8a1e681, 14); ref_snapshot(22,4,(uint32_t[]){b,c,d,a});
	R(f2, b, c, d, a, 4, 0xe7d3fbc8, 20); ref_snapshot(23,4,(uint32_t[]){a,b,c,d});
	R(f2, a, b, c, d, 9, 0x21e1cde6, 5); ref_snapshot(24,4,(uint32_t[]){d,a,b,c});
	R(f2, d, a, b, c, 14, 0xc33707d6, 9); ref_snapshot(25,4,(uint32_t[]){c,d,a,b});
	R(f2, c, d, a, b, 3, 0xf4d50d87, 14); ref_snapshot(26,4,(uint32_t[]){b,c,d,a});
	R(f2, b, c, d, a, 8, 0x455a14ed, 20); ref_snapshot(27,4,(uint32_t[]){a,b,c,d});
	R(f2, a, b, c, d, 13, 0xa9e3e905, 5); ref_snapshot(28,4,(uint32_t[]){d,a,b,c});
	R(f2, d, a, b, c, 2, 0xfcefa3f8, 9); ref_snapshot(29,4,(uint32_t[]){c,d,a,b});
	R(f2, c, d, a, b, 7, 0x676f02d9, 14); ref_snapshot(30,4,(uint32_t[]){b,c,d,a});
	R(f2, b, c, d, a, 12, 0x8d2a4c8a, 20); ref_snapshot(31,4,(uint32_t[]){a,b,c,d});

	R(f3, a, b, c, d, 5, 0xfffa3942, 4); ref_snapshot(32,4,(uint32_t[]){d,a,b,c});
	R(f3, d, a, b, c, 8, 0x8771f681, 11); ref_snapshot(33,4,(uint32_t[]){c,d,a,b});
	R(f3, c, d, a, b, 11, 0x6d9d6122, 16); ref_snapshot(34,4,(uint32_t[]){b,c,d,a});
	R(f3, b, c, d, a, 14, 0xfde5380c, 23); ref_snapshot(35,4,(uint32_t[]){a,b,c,d});
	R(f3, a, b, c, d, 1, 0xa4beea44, 4); ref_snapshot(36,4,(uint32_t[]){d,a,b,c});
	R(f3, d, a, b, c, 4, 0x4bdecfa9, 11); ref_snapshot(37,4,(uint32_t[]){c,d,a,b});
	R(f3, c, d, a, b, 7, 0xf6bb4b60, 16); ref_snapshot(38,4,(uint32_t[]){b,c,d,a});
	R(f3, b, c, d, a, 10, 0xbebfbc70, 23); ref_snapshot(39,4,(uint32_t[]){a,b,c,d});
	R(f3, a, b, c, d, 13, 0x289b7ec6, 4); ref_snapshot(40,4,(uint32_t[]){d,a,b,c});
	R(f3, d, a, b, c, 0, 0xeaa127fa, 11); ref_snapshot(41,4,(uint32_t[]){c,d,a,b});
	R(f3, c, d, a, b, 3, 0xd4ef3085, 16); ref_snapshot(42,4,(uint32_t[]){b,c,d,a});
	R(f3, b, c, d, a, 6, 0x04881d05, 23); ref_snapshot(43,4,(uint32_t[]){a,b,c,d});
	R(f3, a, b, c, d, 9, 0xd9d4d039, 4); ref_snapshot(44,4,(uint32_t[]){d,a,b,c});
	R(f3, d, a, b, c, 12, 0xe6db99e5, 11); ref_snapshot(45,4,(uint32_t[]){c,d,a,b});
	R(f3, c, d, a, b, 15, 0x1fa27cf8, 16); ref_snapshot(46,4,(uint32_t[]){b,c,d,a});
	R(f3, b, c, d, a, 2, 0xc4ac5665, 23); ref_snapshot(47,4,(uint32_t[]){a,b,c,d});

	R(f4, a, b, c, d, 0, 0xf4292244, 6); ref_snapshot(48,4,(uint32_t[]){d,a,b,c});
	R(f4, d, a, b, c, 7, 0x432aff97, 10); ref_snapshot(49,4,(uint32_t[]){c,d,a,b});
	R(f4, c, d, a, b, 14, 0xab9423a7, 15); ref_snapshot(50,4,(uint32_t[]){b,c,d,a});
	R(f4, b, c, d, a, 5, 0xfc93a039, 21); ref_snapshot(51,4,(uint32_t[]){a,b,c,d});
	R(f4, a, b, c, d, 12, 0x655b59c3, 6); ref_snapshot(52,4,(uint32_t[]){d,a,b,c});
	R(f4, d, a, b, c, 3, 0x8f0ccc92, 10); ref_snapshot(53,4,(uint32_t[]){c,d,a,b});
	R(f4, c, d, a, b, 10, 0xffeff47d, 15); ref_snapshot(54,4,(uint32_t[]){b,c,d,a});
	R(f4, b, c, d, a, 1, 0x85845dd1, 21); ref_snapshot(55,4,(uint32_t[]){a,b,c,d});
	R(f4, a, b, c, d, 8, 0x6fa87e4f, 6); ref_snapshot(56,4,(uint32_t[]){d,a,b,c});
	R(f4, d, a, b, c, 15, 0xfe2ce6e0, 10); ref_snapshot(57,4,(uint32_t[]){c,d,a,b});
	R(f4, c, d, a, b, 6, 0xa3014314, 15); ref_snapshot(58,4,(uint32_t[]){b,c,d,a});
	R(f4, b, c, d, a, 13, 0x4e0811a1, 21); ref_snapshot(59,4,(uint32_t[]){a,b,c,d});
	R(f4, a, b, c, d, 4, 0xf7537e82, 6); ref_snapshot(60,4,(uint32_t[]){d,a,b,c});
	R(f4, d, a, b, c, 11, 0xbd3af235, 10); ref_snapshot(61,4,(uint32_t[]){c,d,a,b});
	R(f4, c, d, a, b, 2, 0x2ad7d2bb, 15); ref_snapshot(62,4,(uint32_t[]){b,c,d,a});
	R(f4, b, c, d, a, 9, 0xeb86d391, 21); ref_snapshot(63,4,(uint32_t[]){a,b,c,d});

	ctx->h[0] += a; ctx->h[1] += b; ctx->h[2] += c; ctx->h[3] += d;
}

void crypton_md5_update(struct md5_ctx *ctx, const uint8_t *data, uint32_t len)
{
	uint32_t index, to_fill;

	index = (uint32_t) (ctx->sz & 0x3f);
	to_fill = 64 - index;

	ctx->sz += len;

	if (index && len >= to_fill) {
		memcpy(ctx->buf + index, data, to_fill);
		md5_do_chunk(ctx, (uint32_t *) ctx->buf);
		len -= to_fill;
		data += to_fill;
		index = 0;
	}

	if (need_alignment(data, 4)) {
		uint32_t tramp[16];
		ASSERT_ALIGNMENT(tramp, 4);
		for (; len >= 64; len -= 64, data += 64) {
			memcpy(tramp, data, 64);
			md5_do_chunk(ctx, tramp);
		}
	} else {
		/* process as much 64-block as possible */
		for (; len >= 64; len -= 64, data += 64)
			md5_do_chunk(ctx, (uint32_t *) data);
	}

	/* append data into buf */
	if (len)
		memcpy(ctx->buf + index, data, len);
}

void crypton_md5_finalize(struct md5_ctx *ctx, uint8_t *out)
{
	static uint8_t padding[64] = { 0x80, };
	uint64_t bits;
	uint32_t index, padlen;

	/* add padding and update data with it */
	bits = cpu_to_le64(ctx->sz << 3);

	/* pad out to 56 */
	index = (uint32_t) (ctx->sz & 0x3f);
	padlen = (index < 56) ? (56 - index) : ((64 + 56) - index);
	crypton_md5_update(ctx, padding, padlen);

	/* append length */
	crypton_md5_update(ctx, (uint8_t *) &bits, sizeof(bits));

	/* output hash */
	store_le32(out   , ctx->h[0]);
	store_le32(out+ 4, ctx->h[1]);
	store_le32(out+ 8, ctx->h[2]);
	store_le32(out+12, ctx->h[3]);
}

#define HASHED(m) MD5_##m
#define HASHED_LOWER(m) md5_##m
#define CRYPTON_HASHED(m) crypton_md5_##m
#define MD5_BLOCK_SIZE 64
#define MD5_BITS_ELEMS 1

static inline uint32_t crypton_md5_get_index(const struct md5_ctx *ctx)
{
	return (uint32_t) (ctx->sz & 0x3f);
}

static inline void crypton_md5_incr_sz(struct md5_ctx *ctx, uint64_t *bits, uint32_t n)
{
	ctx->sz += n;
	*bits = cpu_to_le64(ctx->sz << 3);
}

static inline void crypton_md5_select_digest(const struct md5_ctx *ctx, uint8_t *out, uint32_t out_mask)
{
	xor_le32(out   , ctx->h[0] & out_mask);
	xor_le32(out+ 4, ctx->h[1] & out_mask);
	xor_le32(out+ 8, ctx->h[2] & out_mask);
	xor_le32(out+12, ctx->h[3] & out_mask);
}

#include <crypton_hash_prefix.c>
