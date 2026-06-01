/**
 * UUID generation utilities.
 */

const CHARS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'.split('');

/**
 * Generate a UUID string (RFC 4122 v4 format when no len specified).
 */
export function generateUUID(len?: number, radix?: number): string {
    const chars = CHARS;
    const uuid: string[] = [];
    const r = radix || chars.length;

    if (len) {
        // Compact form
        for (let i = 0; i < len; i++) uuid[i] = chars[0 | Math.random() * r];
    } else {
        // rfc4122, version 4 form
        uuid[8] = uuid[13] = uuid[18] = uuid[23] = '-';
        uuid[14] = '4';

        for (let i = 0; i < 36; i++) {
            if (!uuid[i]) {
                const rnd = 0 | Math.random() * 16;
                uuid[i] = chars[(i === 19) ? (rnd & 0x3) | 0x8 : rnd];
            }
        }
    }

    return uuid.join('');
}

/**
 * Fast RFC4122 v4 UUID.
 */
export function generateUUIDFast(): string {
    const chars = CHARS;
    const uuid = new Array<string>(36);
    let rnd = 0;
    for (let i = 0; i < 36; i++) {
        if (i === 8 || i === 13 || i === 18 || i === 23) {
            uuid[i] = '-';
        } else if (i === 14) {
            uuid[i] = '4';
        } else {
            if (rnd <= 0x02) rnd = 0x2000000 + (Math.random() * 0x1000000) | 0;
            const r = rnd & 0xf;
            rnd = rnd >> 4;
            uuid[i] = chars[(i === 19) ? (r & 0x3) | 0x8 : r];
        }
    }
    return uuid.join('');
}

/**
 * Compact RFC4122 v4 UUID (slower but terse code).
 */
export function generateUUIDCompact(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

// Backward compat: extend Math namespace
declare global {
    interface Math {
        uuid?: typeof generateUUID;
        uuidFast?: typeof generateUUIDFast;
        uuidCompact?: typeof generateUUIDCompact;
    }
}

Math.uuid = generateUUID;
Math.uuidFast = generateUUIDFast;
Math.uuidCompact = generateUUIDCompact;
