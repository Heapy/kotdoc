/**
 * @cond internal
 * @file f_arithmetic.c
 * @copyright
 *   Copyright (c) 2014 Cryptography Research, Inc.  \n
 *   Released under the MIT License.  See LICENSE.txt for license information.
 * @author Mike Hamburg
 * @brief Field-specific arithmetic.
 */

#include "field.h"

mask_t crypton_gf_isr (
    gf a,
    const gf x
) {
    gf L0, L1, L2;
    crypton_gf_sqr  (L1,     x );
    df_snapshot("stage0",L1);
    crypton_gf_mul  (L2,     x,   L1 );
    df_snapshot("stage1",L2);
    crypton_gf_sqr  (L1,   L2 );
    df_snapshot("stage2",L1);
    crypton_gf_mul  (L2,     x,   L1 );
    df_snapshot("stage3",L2);
    crypton_gf_sqrn (L1,   L2,     3 );
    df_snapshot("stage4",L1);
    crypton_gf_mul  (L0,   L2,   L1 );
    df_snapshot("stage5",L0);
    crypton_gf_sqrn (L1,   L0,     3 );
    df_snapshot("stage6",L1);
    crypton_gf_mul  (L0,   L2,   L1 );
    df_snapshot("stage7",L0);
    crypton_gf_sqrn (L2,   L0,     9 );
    df_snapshot("stage8",L2);
    crypton_gf_mul  (L1,   L0,   L2 );
    df_snapshot("stage9",L1);
    crypton_gf_sqr  (L0,   L1 );
    df_snapshot("stage10",L0);
    crypton_gf_mul  (L2,     x,   L0 );
    df_snapshot("stage11",L2);
    crypton_gf_sqrn (L0,   L2,    18 );
    df_snapshot("stage12",L0);
    crypton_gf_mul  (L2,   L1,   L0 );
    df_snapshot("stage13",L2);
    crypton_gf_sqrn (L0,   L2,    37 );
    df_snapshot("stage14",L0);
    crypton_gf_mul  (L1,   L2,   L0 );
    df_snapshot("stage15",L1);
    crypton_gf_sqrn (L0,   L1,    37 );
    df_snapshot("stage16",L0);
    crypton_gf_mul  (L1,   L2,   L0 );
    df_snapshot("stage17",L1);
    crypton_gf_sqrn (L0,   L1,   111 );
    df_snapshot("stage18",L0);
    crypton_gf_mul  (L2,   L1,   L0 );
    df_snapshot("stage19",L2);
    crypton_gf_sqr  (L0,   L2 );
    df_snapshot("stage20",L0);
    crypton_gf_mul  (L1,     x,   L0 );
    df_snapshot("stage21",L1);
    crypton_gf_sqrn (L0,   L1,   223 );
    df_snapshot("stage22",L0);
    crypton_gf_mul  (L1,   L2,   L0 );
    df_snapshot("stage23",L1);
    crypton_gf_sqr  (L2, L1);
    df_snapshot("stage24",L2);
    crypton_gf_mul  (L0, L2, x);
    df_snapshot("stage25",L0);
    crypton_gf_copy(a,L1);
    df_snapshot("stage26",a);
    return crypton_gf_eq(L0,ONE);
}
