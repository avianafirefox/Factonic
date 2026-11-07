package com.factonic.game.data

/** Item categories drive grouping in the crafting UI. */
enum class Group(val label: String) {
    RESOURCE("Resources"), MATERIAL("Materials"), COMPONENT("Components"),
    LOGISTICS("Logistics"), PRODUCTION("Production"), POWER("Power"), SCIENCE("Science"),
    EXOTIC("Exotics"), EXPORT("Earth Exports")
}

/**
 * Which machine family can run a recipe. ASSEMBLE recipes can also be hand-crafted.
 * [auto] machines pick their recipe from the first input they receive (smelters, ingot forges).
 * [exotic] families only exist in the high-tier buildings that process Solanite, Terravite and Vitae.
 */
enum class Cat(val auto: Boolean = false, val exotic: Boolean = false, val machine: String = "") {
    SMELT(true, machine = "Smelter / Arc Furnace"), ASSEMBLE(machine = "Assembler"), CHEM(machine = "Chemical Plant"), LAUNCH(machine = "Orbital Launcher"),
    FORGE(true, true, "Ingot Forge"), LIQUEFY(false, true, "Solanite Liquefier"), PULVERIZE(false, true, "Terravite Pulverizer"),
    CURE(false, true, "Curing Kiln"), CENTRIFUGE(false, true, "Bio Centrifuge"), SEQUENCE(false, true, "Gene Sequencer"),
    SYNTH(false, true, "Vitae Synthesizer"), PACK(false, true, "Cargo Packer")
}

class Item(val id: String, val name: String, val group: Group, val desc: String) {
    var idx = 0
    val isScience get() = id.startsWith("sci_")
    val isExport get() = group == Group.EXPORT
    val building: BType? get() = Buildings.byId[id]
    override fun toString() = id
}

data class Stack(val item: Item, val n: Int)

class Recipe(
    val id: String,
    val cat: Cat,
    val time: Float,
    val inputs: List<Stack>,
    val outputs: List<Stack>,
    private val title: String? = null,
) {
    val main: Item get() = outputs.first().item
    val name: String get() = title ?: main.name
}

enum class BKind { BELT, INSERTER, DRILL, RARE_DRILL, EXTRACTOR, BIO_PUMP, CRAFTER, LAB, CHEST, CORE, COMBUSTION, SOLAR, ACCUMULATOR, GEOTHERMAL, LAMP, TUNNEL, SPLITTER }

class BType(
    val id: String,
    val name: String,
    val kind: BKind,
    val size: Int,
    /** kW drawn while working */
    val power: Float,
    val speed: Float = 1f,
    val cat: Cat? = null,
    val rotatable: Boolean = false,
) {
    val sprite get() = id
}

object Items {
    val all = ArrayList<Item>()
    val byId = HashMap<String, Item>()
    private fun i(id: String, name: String, g: Group, d: String) = Item(id, name, g, d).also { it.idx = all.size; all += it; byId[id] = it }

    // raw
    val FERRITE_ORE = i("ferrite_ore", "Ferrite Ore", Group.RESOURCE, "Magnetic blue-grey ore. Smelts into Ferrite Plates.")
    val CUPRITE_ORE = i("cuprite_ore", "Cuprite Ore", Group.RESOURCE, "Copper-rich ore veined with teal minerals.")
    val LUMITE = i("lumite", "Lumite Stone", Group.RESOURCE, "Pale luminous stone. Used for bricks and smelters.")
    val CARBON = i("carbon", "Carbonite", Group.RESOURCE, "Dense carbon deposit. Fuel for combustion generators.")
    val AETHER = i("aether_crystal", "Aether Crystal", Group.RESOURCE, "Resonant violet crystal found far from the landing site.")
    val XENITE = i("xenite_ore", "Xenite Ore", Group.RESOURCE, "Faintly glowing radioactive ore. Handle with care.")
    val SAP = i("sap", "Glowsap", Group.RESOURCE, "Bioluminescent sap pumped from the planet's crust.")

    // materials
    val FERRITE_PLATE = i("ferrite_plate", "Ferrite Plate", Group.MATERIAL, "Basic structural metal.")
    val COPPER_PLATE = i("copper_plate", "Copper Plate", Group.MATERIAL, "Conductive metal for wiring.")
    val BRICK = i("brick", "Lumite Brick", Group.MATERIAL, "Heat-resistant building block.")
    val STEEL = i("steel", "Steel Plate", Group.MATERIAL, "Hardened ferrite alloy.")
    val PLASTIC = i("plastic", "Polymer Bar", Group.MATERIAL, "Lightweight polymer refined from Glowsap.")
    val SULFITE = i("sulfite", "Sulfite", Group.MATERIAL, "Reactive yellow powder.")

    // components
    val GEAR = i("gear", "Ferrite Gear", Group.COMPONENT, "Mechanical drive component.")
    val WIRE = i("wire", "Copper Wire", Group.COMPONENT, "Thin conductive wire.")
    val CIRCUIT = i("circuit", "Circuit", Group.COMPONENT, "Basic logic board.")
    val PIPE = i("pipe", "Pipe", Group.COMPONENT, "Pressure conduit.")
    val ENGINE = i("engine", "Engine Unit", Group.COMPONENT, "Compact mechanical engine.")
    val ADV_CIRCUIT = i("adv_circuit", "Advanced Circuit", Group.COMPONENT, "Multi-layer polymer circuit.")
    val BATTERY = i("battery", "Battery", Group.COMPONENT, "Chemical energy cell.")
    val LENS = i("aether_lens", "Aether Lens", Group.COMPONENT, "Focusing lens grown from Aether Crystal.")
    val PROCESSING = i("processing_unit", "Processing Unit", Group.COMPONENT, "High-density compute core.")
    val LDS = i("lds", "Low-Density Frame", Group.COMPONENT, "Light, strong structural lattice.")
    val XENITE_CELL = i("xenite_cell", "Xenite Cell", Group.COMPONENT, "Enriched xenite power capsule.")
    val ROCKET_FUEL = i("rocket_fuel", "Rocket Fuel", Group.COMPONENT, "Volatile propellant.")
    val ROCKET_SEGMENT = i("rocket_segment", "Rocket Segment", Group.COMPONENT, "One stage of an orbital rocket.")
    val SATELLITE = i("satellite", "Beacon Satellite", Group.COMPONENT, "Relay satellite. Launch it to earn Astral Science.")

    // the three Xyra elements and their processing chains
    val SOLANITE_ORE = i("solanite_ore", "Solanite Ore", Group.RESOURCE, "Amber crystal-metal that hums under your boots. Only a Resonance Drill can break it.")
    val TERRAVITE_ORE = i("terravite_ore", "Terravite Ore", Group.RESOURCE, "Dense grey-green stone threaded with white veins. Only a Resonance Drill can break it.")
    val VITAE_OOZE = i("vitae_ooze", "Vitae Ooze", Group.RESOURCE, "Warm pink protein goo seeping from living springs. It's alive, sort of.")
    val SOLANITE_INGOT = i("solanite_ingot", "Solanite Ingot", Group.EXOTIC, "Purified Solanite, forged at extreme heat.")
    val TERRAVITE_INGOT = i("terravite_ingot", "Terravite Ingot", Group.EXOTIC, "Purified Terravite. Heavier than it looks.")
    val LIQUID_SOLANITE = i("liquid_solanite", "Liquid Solanite", Group.EXOTIC, "Solanite held liquid by a Glowsap solvent. It cures into Xyra Epoxy.")
    val TERRAVITE_POWDER = i("terravite_powder", "Terravite Cement", Group.EXOTIC, "Fine alien cement. Add water and it sets stronger than any alloy.")
    val EPOXY_FILM = i("epoxy_film", "Epoxy Film", Group.EXOTIC, "Solanite cured thin and flexible. Folds like a plastic bag, holds like steel.")
    val EPOXY_BRICK = i("epoxy_brick", "Epoxy Brick", Group.EXOTIC, "Solanite cured solid. Lighter than foam, harder than diamond.")
    val EPOXY_GLASS = i("epoxy_glass", "Epoxy Glass", Group.EXOTIC, "Solanite cured clear. Perfectly transparent and practically unbreakable.")
    val CRYO_COOLANT = i("cryo_coolant", "Cryo Coolant", Group.EXOTIC, "Aether-chilled Glowsap that stays liquid near absolute zero.")
    val VITAE_PLASMA = i("vitae_plasma", "Vitae Plasma", Group.EXOTIC, "Centrifuged Vitae, rich in self-repairing proteins.")
    val BIO_RESIDUE = i("bio_residue", "Bio-Residue", Group.EXOTIC, "Leftover Vitae solids. Can be recycled back into plasma.")
    val TELOMERE = i("telomere_strand", "Telomere Strand", Group.EXOTIC, "Sequenced Vitae protein that rebuilds worn-out DNA.")
    val CELL_LATTICE = i("cell_lattice", "Cell Lattice", Group.EXOTIC, "Protein scaffold bound in Terravite cement and Epoxy Film.")
    val VITAE_ELIXIR = i("vitae_elixir", "Vitae Elixir", Group.EXOTIC, "One dose ends aging and disease for one human being.")
    val EPOXY_CANISTER = i("epoxy_canister", "Xyra Epoxy Canister", Group.EXPORT, "Sealed canister of liquid Xyra Epoxy for Earth's engineers.")
    val XENOCRETE_SACK = i("xenocrete_sack", "Xenocrete Sack", Group.EXPORT, "Terravite cement sealed in an Epoxy Film bag. Ready to rebuild Earth's cities.")
    val VITAE_CRYOCASE = i("vitae_cryocase", "Vitae Cryo-Case", Group.EXPORT, "Four doses of Vitae Elixir in an Epoxy Glass cryo-case. Immortality, shipped.")

    val EXPORTS get() = listOf(EPOXY_CANISTER, XENOCRETE_SACK, VITAE_CRYOCASE)

    // science
    val SCI_K = i("sci_kinetic", "Kinetic Science", Group.SCIENCE, "Red science. Basic mechanics.")
    val SCI_L = i("sci_logistic", "Logistic Science", Group.SCIENCE, "Green science. Transport and automation.")
    val SCI_C = i("sci_chemical", "Chemical Science", Group.SCIENCE, "Blue science. Chemistry and electronics.")
    val SCI_F = i("sci_fabrication", "Fabrication Science", Group.SCIENCE, "Purple science. Advanced fabrication.")
    val SCI_Q = i("sci_quantum", "Quantum Science", Group.SCIENCE, "Yellow science. Exotic physics.")
    val SCI_A = i("sci_astral", "Astral Science", Group.SCIENCE, "White science. Data gathered from orbit.")

    val SCIENCES get() = listOf(SCI_K, SCI_L, SCI_C, SCI_F, SCI_Q, SCI_A)

    init {
        // building items registered after Buildings so that they share ids
        for (b in Buildings.list) {
            val g = when (b.kind) {
                BKind.BELT, BKind.INSERTER, BKind.CHEST, BKind.TUNNEL, BKind.SPLITTER -> Group.LOGISTICS
                BKind.CORE, BKind.COMBUSTION, BKind.SOLAR, BKind.ACCUMULATOR, BKind.GEOTHERMAL, BKind.LAMP -> Group.POWER
                BKind.LAB -> Group.SCIENCE
                BKind.RARE_DRILL, BKind.BIO_PUMP -> Group.EXOTIC
                else -> if (b.cat?.exotic == true) Group.EXOTIC else Group.PRODUCTION
            }
            i(b.id, b.name, g, BuildingDesc.of(b.id))
        }
    }

    operator fun get(id: String): Item = byId[id] ?: error("Unknown item $id")
}

object Buildings {
    val list = listOf(
        BType("belt", "Conveyor Belt", BKind.BELT, 1, 0f, speed = 1.5f, rotatable = true),
        BType("fast_belt", "Fast Belt", BKind.BELT, 1, 0f, speed = 3f, rotatable = true),
        BType("express_belt", "Express Belt", BKind.BELT, 1, 0f, speed = 4.5f, rotatable = true),
        BType("inserter", "Inserter", BKind.INSERTER, 1, 13f, speed = 1.3f, rotatable = true),
        BType("fast_inserter", "Fast Inserter", BKind.INSERTER, 1, 40f, speed = 3.4f, rotatable = true),
        BType("chest", "Storage Crate", BKind.CHEST, 1, 0f),
        BType("drill", "Mining Drill", BKind.DRILL, 2, 90f, speed = 0.5f, rotatable = true),
        BType("deep_drill", "Deep Core Drill", BKind.DRILL, 2, 180f, speed = 1.1f, rotatable = true),
        BType("extractor", "Sap Extractor", BKind.EXTRACTOR, 2, 90f, speed = 1f, rotatable = true),
        BType("smelter", "Smelter", BKind.CRAFTER, 2, 90f, speed = 1f, cat = Cat.SMELT),
        BType("arc_furnace", "Arc Furnace", BKind.CRAFTER, 2, 180f, speed = 2f, cat = Cat.SMELT),
        BType("assembler", "Assembler", BKind.CRAFTER, 2, 75f, speed = 0.75f, cat = Cat.ASSEMBLE),
        BType("assembler2", "Assembler Mk2", BKind.CRAFTER, 2, 150f, speed = 1.5f, cat = Cat.ASSEMBLE),
        BType("chem_plant", "Chemical Plant", BKind.CRAFTER, 2, 210f, speed = 1f, cat = Cat.CHEM),
        BType("launcher", "Orbital Launcher", BKind.CRAFTER, 3, 600f, speed = 1f, cat = Cat.LAUNCH),
        BType("lab", "Research Lab", BKind.LAB, 2, 60f, speed = 1f),
        BType("power_core", "Lander Core", BKind.CORE, 3, 0f),
        BType("combustion_gen", "Combustion Generator", BKind.COMBUSTION, 2, 0f),
        BType("solar", "Solar Array", BKind.SOLAR, 2, 0f),
        BType("accumulator", "Accumulator", BKind.ACCUMULATOR, 1, 0f),
        BType("geothermal", "Geothermal Tap", BKind.GEOTHERMAL, 2, 0f),
        BType("lamp", "Lumen Lamp", BKind.LAMP, 1, 5f),
        BType("resonance_drill", "Resonance Drill", BKind.RARE_DRILL, 2, 450f, speed = 0.35f, rotatable = true),
        BType("vitae_pump", "Vitae Harvester", BKind.BIO_PUMP, 2, 300f, speed = 0.4f, rotatable = true),
        BType("ingot_forge", "Ingot Forge", BKind.CRAFTER, 3, 800f, speed = 1f, cat = Cat.FORGE),
        BType("liquefier", "Solanite Liquefier", BKind.CRAFTER, 3, 600f, speed = 1f, cat = Cat.LIQUEFY),
        BType("pulverizer", "Terravite Pulverizer", BKind.CRAFTER, 3, 600f, speed = 1f, cat = Cat.PULVERIZE),
        BType("curing_kiln", "Curing Kiln", BKind.CRAFTER, 3, 500f, speed = 1f, cat = Cat.CURE),
        BType("bio_centrifuge", "Bio Centrifuge", BKind.CRAFTER, 3, 500f, speed = 1f, cat = Cat.CENTRIFUGE),
        BType("gene_sequencer", "Gene Sequencer", BKind.CRAFTER, 3, 900f, speed = 1f, cat = Cat.SEQUENCE),
        BType("vitae_synth", "Vitae Synthesizer", BKind.CRAFTER, 3, 1200f, speed = 1f, cat = Cat.SYNTH),
        BType("cargo_packer", "Cargo Packer", BKind.CRAFTER, 3, 400f, speed = 1f, cat = Cat.PACK),
        // v1.0 logistics
        BType("tunnel_belt", "Tunnel Belt", BKind.TUNNEL, 1, 0f, speed = 4.5f, rotatable = true),
        BType("splitter", "Belt Splitter", BKind.SPLITTER, 1, 0f, speed = 6f, rotatable = true),
    )
    val byId = list.associateBy { it.id }
    operator fun get(id: String) = byId[id]!!
}

object BuildingDesc {
    fun of(id: String) = when (id) {
        "belt" -> "Moves items. Drag the arrow handle to lay a line."
        "fast_belt" -> "Twice the throughput of a basic belt."
        "express_belt" -> "Triple speed belt for late-game factories."
        "inserter" -> "Moves items from the tile behind it to the tile in front."
        "fast_inserter" -> "A much faster inserter."
        "chest" -> "Stores up to 800 items."
        "drill" -> "Mines ore underneath and outputs to the front."
        "deep_drill" -> "Faster, heavier drill."
        "extractor" -> "Pumps Glowsap. Must be placed over a sap pool."
        "smelter" -> "Smelts ore into plates and bricks."
        "arc_furnace" -> "Electric furnace, twice as fast."
        "assembler" -> "Crafts components automatically. Choose a recipe."
        "assembler2" -> "Faster assembler."
        "chem_plant" -> "Processes Glowsap and crystals."
        "launcher" -> "Launches satellites for Astral Science, or cargo rockets full of exports to Earth."
        "lab" -> "Consumes science packs to research technology."
        "power_core" -> "Your crashed lander. Supplies 400 kW forever."
        "combustion_gen" -> "Burns Carbonite for up to 900 kW."
        "solar" -> "100 kW in daylight, nothing at night."
        "accumulator" -> "Stores 5 MJ of surplus energy."
        "geothermal" -> "1.8 MW from a geothermal vent."
        "lamp" -> "Lights up the alien night."
        "resonance_drill" -> "Shatters Solanite and Terravite with tuned sound. Must sit on a rare deposit."
        "vitae_pump" -> "Gently draws Vitae Ooze from a living spring. Must sit on a Vitae spring."
        "ingot_forge" -> "Purifies Solanite and Terravite ore into ingots. Picks its recipe from the first ore it gets."
        "liquefier" -> "Dissolves Solanite ingots in Glowsap. Also brews Cryo Coolant."
        "pulverizer" -> "Grinds Terravite ingots into alien cement."
        "curing_kiln" -> "Cures Liquid Solanite into film, bricks or glass."
        "bio_centrifuge" -> "Spins Vitae Ooze into plasma."
        "gene_sequencer" -> "Builds telomere strands and cell lattices from Vitae plasma."
        "vitae_synth" -> "Synthesizes the Vitae Elixir. The most advanced machine on Xyra-7."
        "cargo_packer" -> "Packs the three exports for the trip to Earth."
        "tunnel_belt" -> "Carries a belt under up to ${Tunnels.SPAN} tiles. Place an entrance, then an exit further along in the same direction."
        "splitter" -> "Shares items between every belt leading away from it. Set a filter to sort: the filtered item goes out the front, everything else to the sides."
        else -> ""
    }
}

object Recipes {
    val all = ArrayList<Recipe>()
    val byId = HashMap<String, Recipe>()

    private fun r(id: String, cat: Cat, t: Float, ins: List<Pair<String, Int>>, outs: List<Pair<String, Int>>, title: String? = null) {
        val rec = Recipe(id, cat, t, ins.map { Stack(Items[it.first], it.second) }, outs.map { Stack(Items[it.first], it.second) }, title)
        all += rec; byId[id] = rec
    }

    private fun one(id: String, n: Int = 1) = listOf(id to n)

    init {
        Items.all.size // ensure Items initialised
        // smelting
        r("ferrite_plate", Cat.SMELT, 3.2f, one("ferrite_ore"), one("ferrite_plate"))
        r("copper_plate", Cat.SMELT, 3.2f, one("cuprite_ore"), one("copper_plate"))
        r("brick", Cat.SMELT, 3.2f, one("lumite", 2), one("brick"))
        r("steel", Cat.SMELT, 12f, one("ferrite_plate", 5), one("steel"))
        // components
        r("gear", Cat.ASSEMBLE, 0.5f, one("ferrite_plate", 2), one("gear"))
        r("wire", Cat.ASSEMBLE, 0.5f, one("copper_plate"), one("wire", 2))
        r("circuit", Cat.ASSEMBLE, 0.5f, listOf("ferrite_plate" to 1, "wire" to 3), one("circuit"))
        r("pipe", Cat.ASSEMBLE, 0.5f, one("ferrite_plate"), one("pipe"))
        r("engine", Cat.ASSEMBLE, 10f, listOf("steel" to 1, "gear" to 1, "pipe" to 2), one("engine"))
        r("adv_circuit", Cat.ASSEMBLE, 6f, listOf("circuit" to 2, "plastic" to 2, "wire" to 4), one("adv_circuit"))
        r("processing_unit", Cat.ASSEMBLE, 10f, listOf("circuit" to 4, "adv_circuit" to 2, "sulfite" to 1), one("processing_unit"))
        r("lds", Cat.ASSEMBLE, 12f, listOf("steel" to 1, "copper_plate" to 4, "plastic" to 2), one("lds"))
        r("rocket_segment", Cat.ASSEMBLE, 15f, listOf("lds" to 2, "rocket_fuel" to 2, "processing_unit" to 2), one("rocket_segment"))
        r("satellite", Cat.ASSEMBLE, 20f, listOf("solar" to 2, "accumulator" to 2, "processing_unit" to 5, "lds" to 5, "aether_lens" to 2), one("satellite"))
        // chemistry
        r("plastic", Cat.CHEM, 1f, listOf("sap" to 2, "carbon" to 1), one("plastic", 2))
        r("sulfite", Cat.CHEM, 1f, one("sap", 3), one("sulfite", 2))
        r("battery", Cat.CHEM, 4f, listOf("ferrite_plate" to 1, "copper_plate" to 1, "sulfite" to 1), one("battery"))
        r("aether_lens", Cat.CHEM, 5f, listOf("aether_crystal" to 2, "sulfite" to 1), one("aether_lens"))
        r("xenite_cell", Cat.CHEM, 10f, listOf("xenite_ore" to 5, "steel" to 1), one("xenite_cell"))
        r("rocket_fuel", Cat.CHEM, 6f, listOf("sulfite" to 1, "sap" to 2, "carbon" to 1), one("rocket_fuel"))
        // science
        r("sci_kinetic", Cat.ASSEMBLE, 5f, listOf("copper_plate" to 1, "gear" to 1), one("sci_kinetic"))
        r("sci_logistic", Cat.ASSEMBLE, 6f, listOf("inserter" to 1, "belt" to 1), one("sci_logistic"))
        r("sci_chemical", Cat.ASSEMBLE, 24f, listOf("engine" to 2, "adv_circuit" to 3, "sulfite" to 1), one("sci_chemical", 2))
        r("sci_fabrication", Cat.ASSEMBLE, 21f, listOf("arc_furnace" to 1, "aether_lens" to 1, "brick" to 6), one("sci_fabrication", 3))
        r("sci_quantum", Cat.ASSEMBLE, 21f, listOf("processing_unit" to 1, "xenite_cell" to 1, "lds" to 1), one("sci_quantum", 3))
        r("sci_astral", Cat.LAUNCH, 40f, listOf("rocket_segment" to 10, "satellite" to 1), one("sci_astral", 100))
        // buildings
        r("belt", Cat.ASSEMBLE, 0.5f, listOf("gear" to 1, "ferrite_plate" to 1), one("belt", 2))
        r("fast_belt", Cat.ASSEMBLE, 0.5f, listOf("belt" to 1, "gear" to 5), one("fast_belt"))
        r("express_belt", Cat.ASSEMBLE, 0.5f, listOf("fast_belt" to 1, "gear" to 5, "plastic" to 1), one("express_belt"))
        r("inserter", Cat.ASSEMBLE, 0.5f, listOf("circuit" to 1, "gear" to 1, "ferrite_plate" to 1), one("inserter"))
        r("fast_inserter", Cat.ASSEMBLE, 0.5f, listOf("inserter" to 1, "circuit" to 2, "ferrite_plate" to 2), one("fast_inserter"))
        r("chest", Cat.ASSEMBLE, 0.5f, one("ferrite_plate", 8), one("chest"))
        r("tunnel_belt", Cat.ASSEMBLE, 1f, listOf("belt" to 4, "ferrite_plate" to 6, "gear" to 2), one("tunnel_belt", 2))
        r("splitter", Cat.ASSEMBLE, 1f, listOf("circuit" to 3, "belt" to 2, "gear" to 2, "ferrite_plate" to 4), one("splitter"))
        r("drill", Cat.ASSEMBLE, 2f, listOf("circuit" to 3, "gear" to 5, "ferrite_plate" to 10), one("drill"))
        r("deep_drill", Cat.ASSEMBLE, 4f, listOf("drill" to 1, "steel" to 10, "adv_circuit" to 5), one("deep_drill"))
        r("extractor", Cat.ASSEMBLE, 5f, listOf("steel" to 5, "gear" to 10, "circuit" to 5, "pipe" to 10), one("extractor"))
        r("smelter", Cat.ASSEMBLE, 1f, listOf("lumite" to 10, "ferrite_plate" to 4), one("smelter"))
        r("arc_furnace", Cat.ASSEMBLE, 5f, listOf("steel" to 10, "adv_circuit" to 5, "brick" to 10), one("arc_furnace"))
        r("assembler", Cat.ASSEMBLE, 0.5f, listOf("circuit" to 3, "gear" to 5, "ferrite_plate" to 9), one("assembler"))
        r("assembler2", Cat.ASSEMBLE, 0.5f, listOf("assembler" to 1, "adv_circuit" to 3, "gear" to 5, "steel" to 4), one("assembler2"))
        r("chem_plant", Cat.ASSEMBLE, 5f, listOf("steel" to 5, "gear" to 5, "circuit" to 5, "pipe" to 5), one("chem_plant"))
        r("launcher", Cat.ASSEMBLE, 30f, listOf("steel" to 40, "processing_unit" to 20, "brick" to 40, "engine" to 10, "aether_lens" to 10), one("launcher"))
        r("lab", Cat.ASSEMBLE, 2f, listOf("circuit" to 10, "gear" to 10, "belt" to 4), one("lab"))
        r("combustion_gen", Cat.ASSEMBLE, 1f, listOf("ferrite_plate" to 10, "pipe" to 5, "gear" to 5), one("combustion_gen"))
        r("solar", Cat.ASSEMBLE, 10f, listOf("steel" to 5, "circuit" to 15, "copper_plate" to 5), one("solar"))
        r("accumulator", Cat.ASSEMBLE, 10f, listOf("ferrite_plate" to 2, "battery" to 5), one("accumulator"))
        r("geothermal", Cat.ASSEMBLE, 5f, listOf("steel" to 10, "pipe" to 10, "circuit" to 5, "brick" to 10), one("geothermal"))
        r("lamp", Cat.ASSEMBLE, 0.5f, listOf("circuit" to 1, "wire" to 3, "ferrite_plate" to 1), one("lamp"))

        // ---- the three Xyra elements
        r("solanite_ingot", Cat.FORGE, 8f, one("solanite_ore", 3), one("solanite_ingot"))
        r("terravite_ingot", Cat.FORGE, 8f, one("terravite_ore", 3), one("terravite_ingot"))
        r("liquid_solanite", Cat.LIQUEFY, 6f, listOf("solanite_ingot" to 1, "sap" to 2), one("liquid_solanite", 2))
        r("cryo_coolant", Cat.LIQUEFY, 6f, listOf("aether_crystal" to 1, "sap" to 3), one("cryo_coolant", 2))
        r("terravite_powder", Cat.PULVERIZE, 5f, one("terravite_ingot"), one("terravite_powder", 3))
        r("epoxy_film", Cat.CURE, 4f, one("liquid_solanite"), one("epoxy_film", 2))
        r("epoxy_brick", Cat.CURE, 6f, one("liquid_solanite", 2), one("epoxy_brick"))
        r("epoxy_glass", Cat.CURE, 10f, one("liquid_solanite", 3), one("epoxy_glass", 2))
        r("vitae_plasma", Cat.CENTRIFUGE, 10f, one("vitae_ooze", 4), listOf("vitae_plasma" to 2, "bio_residue" to 1))
        r("plasma_recycle", Cat.CENTRIFUGE, 8f, listOf("bio_residue" to 3, "sap" to 2), one("vitae_plasma"), "Residue Recycling")
        r("telomere_strand", Cat.SEQUENCE, 12f, listOf("vitae_plasma" to 3, "processing_unit" to 1), one("telomere_strand"))
        r("cell_lattice", Cat.SEQUENCE, 10f, listOf("vitae_plasma" to 2, "terravite_powder" to 2, "epoxy_film" to 1), one("cell_lattice"))
        r("vitae_elixir", Cat.SYNTH, 20f, listOf("telomere_strand" to 2, "cell_lattice" to 2, "epoxy_glass" to 1, "cryo_coolant" to 2), one("vitae_elixir"))
        r("epoxy_canister", Cat.PACK, 8f, listOf("liquid_solanite" to 6, "epoxy_brick" to 1), one("epoxy_canister"))
        r("xenocrete_sack", Cat.PACK, 8f, listOf("terravite_powder" to 8, "epoxy_film" to 2), one("xenocrete_sack"))
        r("vitae_cryocase", Cat.PACK, 12f, listOf("vitae_elixir" to 4, "epoxy_glass" to 2, "cryo_coolant" to 2), one("vitae_cryocase"))
        // exotic buildings (hand-craftable; the later ones need cured epoxy)
        r("resonance_drill", Cat.ASSEMBLE, 10f, listOf("deep_drill" to 1, "aether_lens" to 4, "processing_unit" to 4, "xenite_cell" to 2), one("resonance_drill"))
        r("ingot_forge", Cat.ASSEMBLE, 10f, listOf("arc_furnace" to 2, "steel" to 20, "processing_unit" to 5, "brick" to 20), one("ingot_forge"))
        r("liquefier", Cat.ASSEMBLE, 10f, listOf("chem_plant" to 1, "steel" to 15, "pipe" to 20, "processing_unit" to 5), one("liquefier"))
        r("pulverizer", Cat.ASSEMBLE, 10f, listOf("steel" to 20, "gear" to 30, "engine" to 6, "processing_unit" to 3), one("pulverizer"))
        r("curing_kiln", Cat.ASSEMBLE, 10f, listOf("brick" to 30, "steel" to 10, "aether_lens" to 4, "processing_unit" to 3), one("curing_kiln"))
        r("vitae_pump", Cat.ASSEMBLE, 10f, listOf("extractor" to 1, "epoxy_glass" to 4, "processing_unit" to 5), one("vitae_pump"))
        r("bio_centrifuge", Cat.ASSEMBLE, 10f, listOf("engine" to 6, "epoxy_brick" to 10, "processing_unit" to 6), one("bio_centrifuge"))
        r("gene_sequencer", Cat.ASSEMBLE, 15f, listOf("processing_unit" to 15, "epoxy_glass" to 10, "aether_lens" to 6, "xenite_cell" to 4), one("gene_sequencer"))
        r("vitae_synth", Cat.ASSEMBLE, 20f, listOf("processing_unit" to 20, "epoxy_brick" to 20, "epoxy_glass" to 20, "xenite_cell" to 6, "lds" to 10), one("vitae_synth"))
        r("cargo_packer", Cat.ASSEMBLE, 10f, listOf("assembler2" to 1, "epoxy_brick" to 10, "lds" to 5, "processing_unit" to 5), one("cargo_packer"))
    }

    operator fun get(id: String) = byId[id]!!
    fun forCat(c: Cat) = all.filter { it.cat == c }
    fun handFor(item: Item) = all.firstOrNull { it.cat == Cat.ASSEMBLE && it.outputs.any { o -> o.item == item } }
    fun smeltFor(item: Item) = all.firstOrNull { it.cat == Cat.SMELT && it.inputs.first().item == item }
    fun autoFor(cat: Cat, item: Item) = all.firstOrNull { it.cat == cat && it.inputs.first().item == item }

    /** Recipes available from the very start. */
    val STARTING = setOf(
        "ferrite_plate", "copper_plate", "brick", "gear", "wire", "circuit", "pipe", "sci_kinetic",
        "belt", "inserter", "chest", "drill", "smelter", "lab", "combustion_gen"
    )
}

enum class Bonus { NONE, MINING, LAB_SPEED, CRAFT_SPEED, VICTORY, CARGO, BOOSTERS }

class Tech(
    val id: String,
    val name: String,
    val icon: String,
    val desc: String,
    val packs: List<Item>,
    val count: Int,
    val unitTime: Float,
    val prereqs: List<String>,
    val unlocks: List<String>,
    val bonus: Bonus = Bonus.NONE,
    val bonusAmount: Float = 0f,
) {
    var tier = 0
    var row = 0
    val totalTime get() = count * unitTime
}

object Techs {
    val all = ArrayList<Tech>()
    val byId = HashMap<String, Tech>()

    private val K get() = Items.SCI_K
    private val L get() = Items.SCI_L
    private val C get() = Items.SCI_C
    private val F get() = Items.SCI_F
    private val Q get() = Items.SCI_Q
    private val A get() = Items.SCI_A

    private fun t(
        id: String, name: String, icon: String, desc: String, packs: List<Item>, count: Int, time: Float,
        pre: List<String>, unlocks: List<String>, bonus: Bonus = Bonus.NONE, amt: Float = 0f
    ) {
        val tech = Tech(id, name, icon, desc, packs, count, time, pre, unlocks, bonus, amt)
        all += tech; byId[id] = tech
    }

    init {
        Recipes.all.size
        t("automation", "Automation", "assembler", "Assemblers craft components for you.", listOf(K), 10, 10f, listOf(), listOf("assembler"))
        t("illumination", "Illumination", "lamp", "Lumen Lamps push back the alien night.", listOf(K), 10, 10f, listOf(), listOf("lamp"))
        t("steel", "Steel Processing", "steel", "Smelt ferrite plates into steel.", listOf(K), 40, 5f, listOf(), listOf("steel"))
        t("logistic_sci", "Logistic Science", "sci_logistic", "Unlocks green science packs.", listOf(K), 60, 5f, listOf("automation"), listOf("sci_logistic"))
        t("fast_inserter", "Fast Inserter", "fast_inserter", "Speedier item handling.", listOf(K), 30, 10f, listOf("automation"), listOf("fast_inserter"))
        t("tunnels", "Tunnel Belts", "tunnel_belt", "Run belts underneath each other so production lines can cross.", listOf(K), 30, 10f, listOf("automation"), listOf("tunnel_belt"))
        t("research_1", "Research Speed I", "tech_research", "Labs work 25% faster.", listOf(K, L), 80, 15f, listOf("logistic_sci"), listOf(), Bonus.LAB_SPEED, .25f)
        t("logistics_2", "Logistics II", "fast_belt", "Fast belts double throughput.", listOf(K, L), 100, 10f, listOf("logistic_sci"), listOf("fast_belt"))
        t("splitters", "Belt Splitting", "splitter", "Splitters share one belt between several, merge belts together and sort mixed belts with a filter.", listOf(K), 50, 10f, listOf("tunnels"), listOf("splitter"))
        t("mining_1", "Mining Productivity I", "tech_mining", "Drills mine 20% faster.", listOf(K, L), 100, 15f, listOf("logistic_sci"), listOf(), Bonus.MINING, .2f)
        t("engines", "Engines", "engine", "Compact engine units.", listOf(K, L), 80, 10f, listOf("steel", "logistic_sci"), listOf("engine"))
        t("solar", "Solar Energy", "solar", "Free power while the sun shines.", listOf(K, L), 100, 15f, listOf("steel", "logistic_sci"), listOf("solar"))
        t("automation_2", "Automation II", "assembler2", "Faster assemblers.", listOf(K, L), 100, 10f, listOf("logistic_sci", "steel"), listOf("assembler2"))
        t("sap", "Sap Extraction", "extractor", "Pump Glowsap from sap pools.", listOf(K, L), 100, 10f, listOf("logistic_sci", "steel"), listOf("extractor"))
        t("polymers", "Polymer Chemistry", "chem_plant", "Chemical plants, polymer bars and sulfite.", listOf(K, L), 100, 10f, listOf("sap"), listOf("chem_plant", "plastic", "sulfite"))
        t("adv_electronics", "Advanced Electronics", "adv_circuit", "Multi-layer circuits.", listOf(K, L), 150, 10f, listOf("polymers"), listOf("adv_circuit"))
        t("batteries", "Batteries", "battery", "Chemical energy storage.", listOf(K, L), 120, 10f, listOf("polymers"), listOf("battery"))
        t("chemical_sci", "Chemical Science", "sci_chemical", "Unlocks blue science packs.", listOf(K, L), 75, 15f, listOf("adv_electronics", "engines"), listOf("sci_chemical"))
        t("energy_storage", "Energy Storage", "accumulator", "Accumulators store surplus power.", listOf(K, L, C), 120, 15f, listOf("batteries", "solar", "chemical_sci"), listOf("accumulator"))
        t("aether", "Aether Refining", "aether_lens", "Grow focusing lenses from Aether Crystal.", listOf(K, L, C), 120, 15f, listOf("chemical_sci"), listOf("aether_lens"))
        t("geothermal", "Geothermal Power", "geothermal", "Tap the planet's molten vents.", listOf(K, L, C), 150, 15f, listOf("chemical_sci"), listOf("geothermal"))
        t("deep_drill", "Deep Core Drilling", "deep_drill", "Heavy drills that mine twice as fast.", listOf(K, L, C), 150, 15f, listOf("chemical_sci", "mining_1"), listOf("deep_drill"))
        t("arc", "Arc Smelting", "arc_furnace", "Electric arc furnaces smelt twice as fast.", listOf(K, L, C), 200, 15f, listOf("chemical_sci"), listOf("arc_furnace"))
        t("processing", "Processing Units", "processing_unit", "High-density computing.", listOf(K, L, C), 250, 15f, listOf("chemical_sci"), listOf("processing_unit"))
        t("lds", "Low-Density Frames", "lds", "Light structural lattices.", listOf(K, L, C), 250, 15f, listOf("chemical_sci"), listOf("lds"))
        t("fabrication_sci", "Fabrication Science", "sci_fabrication", "Unlocks purple science packs.", listOf(K, L, C), 200, 20f, listOf("arc", "aether"), listOf("sci_fabrication"))
        t("logistics_3", "Logistics III", "express_belt", "Express belts.", listOf(K, L, C, F), 200, 15f, listOf("fabrication_sci", "logistics_2"), listOf("express_belt"))
        t("mining_2", "Mining Productivity II", "tech_mining", "Drills mine another 30% faster.", listOf(K, L, C, F), 250, 20f, listOf("fabrication_sci", "deep_drill"), listOf(), Bonus.MINING, .3f)
        t("research_2", "Research Speed II", "tech_research", "Labs work another 40% faster.", listOf(K, L, C, F), 250, 20f, listOf("fabrication_sci", "research_1"), listOf(), Bonus.LAB_SPEED, .4f)
        t("xenite", "Xenite Enrichment", "xenite_cell", "Enrich xenite ore into power cells.", listOf(K, L, C, F), 250, 20f, listOf("fabrication_sci"), listOf("xenite_cell"))
        t("rocket_fuel", "Rocket Fuel", "rocket_fuel", "Volatile propellant for orbital flight.", listOf(K, L, C, F), 250, 20f, listOf("fabrication_sci"), listOf("rocket_fuel"))
        t("quantum_sci", "Quantum Science", "sci_quantum", "Unlocks yellow science packs.", listOf(K, L, C, F), 300, 20f, listOf("xenite", "processing", "lds"), listOf("sci_quantum"))
        t("orbital", "Orbital Launch", "launcher", "Build the Orbital Launcher, rocket segments and satellites.", listOf(K, L, C, F, Q), 400, 25f, listOf("quantum_sci", "rocket_fuel"), listOf("launcher", "rocket_segment", "satellite", "sci_astral"))
        t("signal", "Earth Uplink", "tech_signal", "Open a permanent link to Earth. Earth answers with a request: the three elements found only on Xyra-7.", listOf(K, L, C, F, Q, A), 300, 30f, listOf("orbital"), listOf(), Bonus.VICTORY)
        val ALL = listOf(K, L, C, F, Q, A)
        t("resonance", "Resonance Mining", "resonance_drill", "Tuned drills that break Solanite and Terravite, and the forge that purifies them.", ALL, 200, 30f, listOf("signal"),
            listOf("resonance_drill", "ingot_forge", "solanite_ingot", "terravite_ingot"))
        t("liquefaction", "Solanite Liquefaction", "liquefier", "Dissolve Solanite into liquid epoxy. Also unlocks Cryo Coolant.", ALL, 200, 30f, listOf("resonance"),
            listOf("liquefier", "liquid_solanite", "cryo_coolant"))
        t("milling", "Terravite Milling", "pulverizer", "Grind Terravite into the strongest cement ever made.", ALL, 200, 30f, listOf("resonance"), listOf("pulverizer", "terravite_powder"))
        t("curing", "Epoxy Curing", "curing_kiln", "Cure liquid Solanite into flexible film, solid bricks or clear glass.", ALL, 250, 30f, listOf("liquefaction"),
            listOf("curing_kiln", "epoxy_film", "epoxy_brick", "epoxy_glass"))
        t("cargo", "Cargo Packing", "cargo_packer", "Pack Epoxy Canisters and Xenocrete Sacks for cargo rockets to Earth.", ALL, 250, 30f, listOf("curing", "milling"),
            listOf("cargo_packer", "epoxy_canister", "xenocrete_sack"))
        t("vitae", "Vitae Biology", "vitae_pump", "Harvest Vitae Ooze from living springs and centrifuge it into plasma.", ALL, 300, 30f, listOf("curing"),
            listOf("vitae_pump", "bio_centrifuge", "vitae_plasma", "plasma_recycle"))
        t("genome", "Gene Sequencing", "gene_sequencer", "Sequence Vitae proteins into telomere strands and cell lattices.", ALL, 350, 30f, listOf("vitae", "milling"),
            listOf("gene_sequencer", "telomere_strand", "cell_lattice"))
        t("heavy_cargo", "Heavy Cargo Holds", "tech_cargo", "Cargo rockets carry 50% more.", ALL, 300, 30f, listOf("cargo"), listOf(), Bonus.CARGO, .5f)
        t("immortality", "Vitae Elixir", "vitae_elixir", "The end of aging and disease. Synthesize the Elixir and pack it in cryo-cases.", ALL, 500, 30f, listOf("genome", "cargo"),
            listOf("vitae_synth", "vitae_elixir", "vitae_cryocase"))
        t("boosters", "Reusable Boosters", "rocket_segment", "Every rocket needs 4 fewer segments.", ALL, 400, 30f, listOf("heavy_cargo"), listOf(), Bonus.BOOSTERS, 4f)
        layout()
    }

    private fun layout() {
        fun tierOf(t: Tech): Int {
            if (t.tier > 0) return t.tier
            t.tier = 1 + (t.prereqs.maxOfOrNull { tierOf(byId[it]!!) } ?: 0)
            return t.tier
        }
        all.forEach { tierOf(it) }
        all.groupBy { it.tier }.forEach { (_, list) -> list.forEachIndexed { i, t -> t.row = i } }
    }

    operator fun get(id: String) = byId[id]!!
}

object Tunnels {
    /** How many tiles a tunnel can pass under (the exit sits up to SPAN + 1 tiles past the entrance). */
    const val SPAN = 5
}

object Content {
    /** Force deterministic init order. */
    fun init() {
        Buildings.list.size; Items.all.size; Recipes.all.size; Techs.all.size
    }
}
