package com.ngstudio.inchperfect.game.render

/** Look of one district. Colors are 0xRRGGBB. */
class DistrictLook(
    val asphalt: Int,
    val asphaltSpeck: Int,
    val lot: Int,
    val road: Int,
    val line: Int,
    val lineYellow: Int,
    val outside: Int,
    val outsideKind: GroundKind,
    val wall: Int,
    val wallTop: Int,
    val barrier: Int,
    val hedge: Int,
    val roofs: IntArray,
    val sidewalk: Int,
    val container: IntArray,
    val accent: Int,
    val groundKind: GroundKind = GroundKind.ASPHALT,
)

enum class GroundKind { ASPHALT, CONCRETE, COBBLE, GRASS, SAND, SNOW, WOOD, PLATE, EPOXY, PAVERS, WATER }

/** Purchasable re-skins of the whole playfield. */
enum class WorldSkin(val id: String, val title: String, val price: Int) {
    CLASSIC("classic", "Classic", 0),
    GOLDEN("golden", "Golden Hour", 5_000),
    BLUEPRINT("blueprint", "Blueprint", 9_000),
    AUTUMN("autumn", "Autumn Leaves", 14_000),
    TOY("toy", "Toy Town", 18_000),
    PIXEL("pixel", "Pixel Retro", 25_000),
    ;
    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: CLASSIC }
}

object Looks {
    private fun look(
        asphalt: Int, lot: Int, road: Int, outside: Int, outsideKind: GroundKind, wall: Int, wallTop: Int, barrier: Int,
        hedge: Int, roofs: IntArray, sidewalk: Int, container: IntArray, accent: Int, ground: GroundKind = GroundKind.ASPHALT,
        line: Int = 0xF4F6FA, yellow: Int = 0xFFC93C, speck: Int = 0x9AA0A8,
    ) = DistrictLook(asphalt, speck, lot, road, line, yellow, outside, outsideKind, wall, wallTop, barrier, hedge, roofs, sidewalk, container, accent, ground)

    private val roofsCity = intArrayOf(0x5B6475, 0x6E5A4E, 0x4E5D6E, 0x7A6F64, 0x3F4A5A, 0x8A7A6A)
    private val containers = intArrayOf(0xC0392B, 0x2471A3, 0x1E8449, 0xD68910, 0x7D3C98, 0x17A589, 0x515A5A)

    val ALL: List<DistrictLook> = listOf(
        // 0 Sunny Suburbs
        look(0x3B3F46, 0x41464E, 0x34383F, 0x5E8E3E, GroundKind.GRASS, 0xB9A58C, 0xD8C9B4, 0xD5D8DD, 0x3E8E4A, intArrayOf(0xA8553F, 0x8E5B47, 0x5D6D7E, 0xB0703F), 0xBFC4CA, containers, 0x7FD36B),
        // 1 Downtown
        look(0x30343B, 0x383C44, 0x2A2E34, 0x4A4F57, GroundKind.PAVERS, 0x8E95A1, 0xB4BAC4, 0xCED2D8, 0x3A7D44, roofsCity, 0xA9AEB6, containers, 0x5AA9FF),
        // 2 Harbor
        look(0x3A3E44, 0x42464C, 0x363A40, 0x1F4E66, GroundKind.WATER, 0x7C8792, 0x9BA6B2, 0xE0B33A, 0x3B7A45, roofsCity, 0x9EA4AA, containers, 0x3EC1C9, GroundKind.CONCRETE),
        // 3 Mall underground
        look(0x4A4E55, 0x52565D, 0x464A51, 0x2B2E34, GroundKind.CONCRETE, 0x9BA1AA, 0xC5CAD1, 0xF2C14E, 0x3B7A45, intArrayOf(0x3B3F46, 0x44484F), 0x8D939B, containers, 0xB0B8C8, GroundKind.EPOXY),
        // 4 Night market
        look(0x2A2C35, 0x33353F, 0x262832, 0x1C1D24, GroundKind.PAVERS, 0x3D3550, 0x5A4D78, 0xE0E3EA, 0x2F6B3C, intArrayOf(0x3A2F4F, 0x2F3A52, 0x4A2F3F, 0x2D2D3D), 0x5A5866, containers, 0xFF3DA5),
        // 5 Rainy boulevard
        look(0x2C3036, 0x34383F, 0x282C32, 0x3A5A3A, GroundKind.GRASS, 0x6D7480, 0x8E95A1, 0xCED2D8, 0x2F6E3C, roofsCity, 0x8D939B, containers, 0x6C8BFF),
        // 6 Airport
        look(0x44484F, 0x4C5058, 0x3E4249, 0x6A8F4E, GroundKind.GRASS, 0xA9B0BA, 0xD7DCE3, 0xF2F4F7, 0x3E8E4A, intArrayOf(0xB8C0CA, 0x9AA3AE), 0xBFC4CA, containers, 0xF2F5FF, GroundKind.CONCRETE),
        // 7 Desert motel
        look(0x4A4540, 0x544E48, 0x45403B, 0xD9B77A, GroundKind.SAND, 0xC9946A, 0xE0B48C, 0xE8D8C0, 0x6E8B3D, intArrayOf(0xC27C4E, 0xB5694A, 0xD9A066), 0xD2C2A8, containers, 0xF0B35A),
        // 8 Snow peak
        look(0x3E434B, 0x48505A, 0x3A3F47, 0xE8F0F6, GroundKind.SNOW, 0x8B6E57, 0xEEF4F9, 0xE34B4B, 0x2E5E4E, intArrayOf(0x6E4E3A, 0x7D5A44, 0x5E6E7E), 0xDDE6EE, containers, 0xBFE9FF),
        // 9 Stadium
        look(0x33373E, 0x3B3F47, 0x2F333A, 0x3F8A3A, GroundKind.GRASS, 0x9AA1AB, 0xC9CED6, 0xF2F4F7, 0x3E8E4A, intArrayOf(0x5A6478, 0x6A7488), 0xB7BCC3, containers, 0x9BFF3C),
        // 10 Old town
        look(0x4B4540, 0x57504A, 0x48423D, 0x6B5B4D, GroundKind.COBBLE, 0xB08A6A, 0xD2B08E, 0xD9CFC2, 0x4A7A3E, intArrayOf(0xA0522D, 0xB5651D, 0x8B4513, 0x9C5B3B), 0xBBAE9E, containers, 0xC98A5B, GroundKind.COBBLE),
        // 11 Industrial
        look(0x3C3F43, 0x464A4F, 0x383B40, 0x4E4F52, GroundKind.CONCRETE, 0x6F747A, 0x8E949B, 0xFFC21A, 0x4A6B3E, intArrayOf(0x5E6268, 0x6E5E4E, 0x4E5862), 0x8F9499, containers, 0xFFB020, GroundKind.CONCRETE),
        // 12 Rooftops
        look(0x4E535B, 0x585D66, 0x4A4F57, 0x22262E, GroundKind.CONCRETE, 0xA0A7B1, 0xC8CED6, 0xE34B4B, 0x3E8E4A, intArrayOf(0x6B7380, 0x7B8390), 0x9BA1A9, containers, 0x8FD3FF, GroundKind.PLATE),
        // 13 Tech park
        look(0x34373D, 0x3D4047, 0x30333A, 0x4E7A3E, GroundKind.GRASS, 0x7E8792, 0xA7B0BA, 0xFFC21A, 0x3E7D4A, intArrayOf(0x5E6874, 0x6E7884, 0x4E5864), 0x9AA1A9, containers, 0x8FA3B8),
        // 14 Beach
        look(0x55524C, 0x605C55, 0x504D47, 0xF1D9A0, GroundKind.SAND, 0xE6D3B3, 0xF5E9D6, 0xF2F4F7, 0x5E9E4A, intArrayOf(0x3FA7C9, 0xF2A65A, 0xE8E8E8), 0xE2D4BA, containers, 0xFFE28A, GroundKind.WOOD),
        // 15 Construction
        look(0x45423E, 0x4E4B46, 0x403D39, 0x8A7356, GroundKind.SAND, 0x8E8E8E, 0xB0B0B0, 0xFF7A1A, 0x5A7A3A, intArrayOf(0x8E8E8E, 0x7E6E5E), 0xA8A39A, containers, 0xFF7A1A, GroundKind.CONCRETE),
        // 16 Royal gardens
        look(0x5A5650, 0x66625B, 0x55514B, 0x4E9A48, GroundKind.GRASS, 0xD8CFC0, 0xEDE6DA, 0xF2EEE6, 0x2F7D3A, intArrayOf(0x8C7B6A, 0xA89A88), 0xE5DDD0, containers, 0x4CD18A, GroundKind.PAVERS),
        // 17 Pit lane
        look(0x2E3136, 0x373A40, 0x2A2D32, 0x3F8A3A, GroundKind.GRASS, 0xE34B4B, 0xF2F4F7, 0xF2F4F7, 0x3E8E4A, intArrayOf(0x3A3F48, 0x484E58), 0xB7BCC3, containers, 0xFF4D4D),
        // 18 Tunnels
        look(0x2B2D31, 0x34363B, 0x27292D, 0x15161A, GroundKind.CONCRETE, 0x5A5E66, 0x767B84, 0xFFC21A, 0x2F5E3C, intArrayOf(0x2E3036, 0x34363C), 0x5E6268, containers, 0x7B61FF, GroundKind.CONCRETE),
        // 19 Legends plaza
        look(0x3A3C43, 0x45474F, 0x34363C, 0x2E2A40, GroundKind.PAVERS, 0xC9A94A, 0xE8D08A, 0xF2F4F7, 0x2F7D3A, intArrayOf(0x4A3F6A, 0x6A4F3A, 0x3A4F6A), 0xC8C0AA, containers, 0xE8C15A, GroundKind.PAVERS),
    )

    fun of(district: Int) = ALL[district.coerceIn(0, ALL.size - 1)]

    /** Autumn Leaves skin: orange/red foliage and ochre grass on any district */
    fun autumn(l: DistrictLook) = DistrictLook(
        l.asphalt, l.asphaltSpeck, l.lot, l.road, l.line, l.lineYellow,
        if (l.outsideKind == GroundKind.GRASS) 0xA8863A else l.outside, l.outsideKind, l.wall, l.wallTop, l.barrier,
        0xC0602A, l.roofs, l.sidewalk, l.container, 0xE07A2A, l.groundKind,
    )
}
