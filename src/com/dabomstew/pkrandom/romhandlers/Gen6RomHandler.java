package com.dabomstew.pkrandom.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  Gen6RomHandler.java - randomizer handler for X/Y/OR/AS.               --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer" by Dabomstew                   --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2012.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import com.dabomstew.pkrandom.FileFunctions;
import com.dabomstew.pkrandom.GFXFunctions;
import com.dabomstew.pkrandom.MiscTweak;
import com.dabomstew.pkrandom.RomFunctions;
import com.dabomstew.pkrandom.constants.Gen6Constants;
import com.dabomstew.pkrandom.constants.GlobalConstants;
import com.dabomstew.pkrandom.ctr.GARCArchive;
import com.dabomstew.pkrandom.ctr.Mini;
import com.dabomstew.pkrandom.exceptions.RandomizerIOException;
import com.dabomstew.pkrandom.newnds.NARCArchive;
import com.dabomstew.pkrandom.pokemon.*;
import pptxt.N3DSTxtHandler;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Gen6RomHandler extends Abstract3DSRomHandler {

    public static class Factory extends RomHandler.Factory {

        @Override
        public Gen6RomHandler create(Random random, PrintStream logStream) {
            return new Gen6RomHandler(random, logStream);
        }

        public boolean isLoadable(String filename) {
            return detect3DSRomInner(getProductCodeFromFile(filename), getTitleIdFromFile(filename));
        }
    }

    public Gen6RomHandler(Random random) {
        super(random, null);
    }

    public Gen6RomHandler(Random random, PrintStream logStream) {
        super(random, logStream);
    }

    private static class OffsetWithinEntry {
        private int entry;
        private int offset;
    }

    private static class RomEntry {
        private String name;
        private String romCode;
        private String titleId;
        private int romType;
        private boolean staticPokemonSupport = true, copyStaticPokemon = true;
        private Map<String, String> strings = new HashMap<>();
        private Map<String, Integer> numbers = new HashMap<>();
        private Map<String, int[]> arrayEntries = new HashMap<>();
        private Map<String, OffsetWithinEntry[]> offsetArrayEntries = new HashMap<>();

        private int getInt(String key) {
            if (!numbers.containsKey(key)) {
                numbers.put(key, 0);
            }
            return numbers.get(key);
        }

        private String getString(String key) {
            if (!strings.containsKey(key)) {
                strings.put(key, "");
            }
            return strings.get(key);
        }
    }

    private static List<RomEntry> roms;

    static {
        loadROMInfo();
    }

    private static void loadROMInfo() {
        roms = new ArrayList<>();
        RomEntry current = null;
        try {
            Scanner sc = new Scanner(FileFunctions.openConfig("gen6_offsets.ini"), "UTF-8");
            while (sc.hasNextLine()) {
                String q = sc.nextLine().trim();
                if (q.contains("//")) {
                    q = q.substring(0, q.indexOf("//")).trim();
                }
                if (!q.isEmpty()) {
                    if (q.startsWith("[") && q.endsWith("]")) {
                        // New rom
                        current = new RomEntry();
                        current.name = q.substring(1, q.length() - 1);
                        roms.add(current);
                    } else {
                        String[] r = q.split("=", 2);
                        if (r.length == 1) {
                            System.err.println("invalid entry " + q);
                            continue;
                        }
                        if (r[1].endsWith("\r\n")) {
                            r[1] = r[1].substring(0, r[1].length() - 2);
                        }
                        r[1] = r[1].trim();
                        if (r[0].equals("Game")) {
                            current.romCode = r[1];
                        } else if (r[0].equals("Type")) {
                            if (r[1].equalsIgnoreCase("ORAS")) {
                                current.romType = Gen6Constants.Type_ORAS;
                            } else {
                                current.romType = Gen6Constants.Type_XY;
                            }
                        } else if (r[0].equals("TitleId")) {
                            current.titleId = r[1];
                        } else if (r[0].equals("CopyFrom")) {
                            for (RomEntry otherEntry : roms) {
                                if (r[1].equalsIgnoreCase(otherEntry.romCode)) {
                                    // copy from here
                                    current.arrayEntries.putAll(otherEntry.arrayEntries);
                                    current.numbers.putAll(otherEntry.numbers);
                                    current.strings.putAll(otherEntry.strings);
                                    current.offsetArrayEntries.putAll(otherEntry.offsetArrayEntries);
//                                    if (current.copyStaticPokemon) {
//                                        current.staticPokemon.addAll(otherEntry.staticPokemon);
//                                        current.staticPokemonSupport = true;
//                                    } else {
//                                        current.staticPokemonSupport = false;
//                                    }
                                }
                            }
                        } else if (r[1].startsWith("[") && r[1].endsWith("]")) {
                            String[] offsets = r[1].substring(1, r[1].length() - 1).split(",");
                            if (offsets.length == 1 && offsets[0].trim().isEmpty()) {
                                current.arrayEntries.put(r[0], new int[0]);
                            } else {
                                int[] offs = new int[offsets.length];
                                int c = 0;
                                for (String off : offsets) {
                                    offs[c++] = parseRIInt(off);
                                }
                                current.arrayEntries.put(r[0], offs);
                            }
                        } else if (r[0].endsWith("Offset") || r[0].endsWith("Count") || r[0].endsWith("Number")) {
                            int offs = parseRIInt(r[1]);
                            current.numbers.put(r[0], offs);
                        } else {
                            current.strings.put(r[0],r[1]);
                        }
                    }
                }
            }
            sc.close();
        } catch (FileNotFoundException e) {
            System.err.println("File not found!");
        }
    }

    private static int parseRIInt(String off) {
        int radix = 10;
        off = off.trim().toLowerCase();
        if (off.startsWith("0x") || off.startsWith("&h")) {
            radix = 16;
            off = off.substring(2);
        }
        try {
            return Integer.parseInt(off, radix);
        } catch (NumberFormatException ex) {
            System.err.println("invalid base " + radix + "number " + off);
            return 0;
        }
    }

    // This ROM
    private Pokemon[] pokes;
    private Map<Integer,FormeInfo> formeMappings = new TreeMap<>();
    private Map<Integer,Map<Integer,Integer>> absolutePokeNumByBaseForme;
    private Map<Integer,Integer> dummyAbsolutePokeNums;
    private List<Pokemon> pokemonList;
    private List<Pokemon> pokemonListInclFormes;
    private List<MegaEvolution> megaEvolutions;
    private Move[] moves;
    private RomEntry romEntry;
    private byte[] code;
    private List<String> abilityNames;
    private boolean loadedWildMapNames;
    private Map<Integer, String> wildMapNames;
    private List<String> itemNames;

    private GARCArchive pokeGarc, moveGarc, stringsGarc, storyTextGarc;

    @Override
    protected boolean detect3DSRom(String productCode, String titleId) {
        return detect3DSRomInner(productCode, titleId);
    }

    private static boolean detect3DSRomInner(String productCode, String titleId) {
        return entryFor(productCode, titleId) != null;
    }

    private static RomEntry entryFor(String productCode, String titleId) {
        if (productCode == null || titleId == null) {
            return null;
        }

        for (RomEntry re : roms) {
            if (productCode.equals(re.romCode) && titleId.equals(re.titleId)) {
                return re;
            }
        }
        return null;
    }

    @Override
    protected void loadedROM(String productCode, String titleId) {
        this.romEntry = entryFor(productCode, titleId);

        try {
            code = readCode();
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }

        try {
            stringsGarc = readGARC(romEntry.getString("TextStrings"),true);
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }

        loadPokemonStats();
        loadMoves();

        pokemonListInclFormes = Arrays.asList(pokes);
        pokemonList = Arrays.asList(Arrays.copyOfRange(pokes,0,Gen6Constants.pokemonCount + 1));

        abilityNames = getStrings(false,romEntry.getInt("AbilityNamesTextOffset"));
        itemNames = getStrings(false,romEntry.getInt("ItemNamesTextOffset"));

        loadedWildMapNames = false;
    }

    private void loadPokemonStats() {
        try {
            pokeGarc = this.readGARC(romEntry.getString("PokemonStats"),true);
            String[] pokeNames = readPokemonNames();
            int formeCount = Gen6Constants.getFormeCount(romEntry.romType);
            pokes = new Pokemon[Gen6Constants.pokemonCount + formeCount + 1];
            for (int i = 1; i <= Gen6Constants.pokemonCount; i++) {
                pokes[i] = new Pokemon();
                pokes[i].number = i;
                loadBasicPokeStats(pokes[i],pokeGarc.files.get(i).get(0),formeMappings);
                pokes[i].name = pokeNames[i];
            }

            absolutePokeNumByBaseForme = new HashMap<>();
            dummyAbsolutePokeNums = new HashMap<>();
            dummyAbsolutePokeNums.put(0,0);

            int i = Gen6Constants.pokemonCount + 1;
            int formNum = 1;
            int prevSpecies = 0;
            Map<Integer,Integer> currentMap = new HashMap<>();
            for (int k: formeMappings.keySet()) {
                pokes[i] = new Pokemon();
                pokes[i].number = i;
                loadBasicPokeStats(pokes[i], pokeGarc.files.get(k).get(0),formeMappings);
                FormeInfo fi = formeMappings.get(k);
                pokes[i].name = pokeNames[fi.baseForme];
                pokes[i].baseForme = pokes[fi.baseForme];
                pokes[i].formeNumber = fi.formeNumber;
                pokes[i].formeSuffix = Gen6Constants.formeSuffixes.getOrDefault(k,"");
                if (fi.baseForme == prevSpecies) {
                    formNum++;
                    currentMap.put(formNum,i);
                } else {
                    if (prevSpecies != 0) {
                        absolutePokeNumByBaseForme.put(prevSpecies,currentMap);
                    }
                    prevSpecies = fi.baseForme;
                    formNum = 1;
                    currentMap = new HashMap<>();
                    currentMap.put(formNum,i);
                }
                i++;
            }
            if (prevSpecies != 0) {
                absolutePokeNumByBaseForme.put(prevSpecies,currentMap);
            }
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
        populateEvolutions();
        populateMegaEvolutions();
    }

    private void loadBasicPokeStats(Pokemon pkmn, byte[] stats, Map<Integer,FormeInfo> altFormes) {
        pkmn.hp = stats[Gen6Constants.bsHPOffset] & 0xFF;
        pkmn.attack = stats[Gen6Constants.bsAttackOffset] & 0xFF;
        pkmn.defense = stats[Gen6Constants.bsDefenseOffset] & 0xFF;
        pkmn.speed = stats[Gen6Constants.bsSpeedOffset] & 0xFF;
        pkmn.spatk = stats[Gen6Constants.bsSpAtkOffset] & 0xFF;
        pkmn.spdef = stats[Gen6Constants.bsSpDefOffset] & 0xFF;
        // Type
        pkmn.primaryType = Gen6Constants.typeTable[stats[Gen6Constants.bsPrimaryTypeOffset] & 0xFF];
        pkmn.secondaryType = Gen6Constants.typeTable[stats[Gen6Constants.bsSecondaryTypeOffset] & 0xFF];
        // Only one type?
        if (pkmn.secondaryType == pkmn.primaryType) {
            pkmn.secondaryType = null;
        }
        pkmn.catchRate = stats[Gen6Constants.bsCatchRateOffset] & 0xFF;
        pkmn.growthCurve = ExpCurve.fromByte(stats[Gen6Constants.bsGrowthCurveOffset]);

        pkmn.ability1 = stats[Gen6Constants.bsAbility1Offset] & 0xFF;
        pkmn.ability2 = stats[Gen6Constants.bsAbility2Offset] & 0xFF;
        pkmn.ability3 = stats[Gen6Constants.bsAbility3Offset] & 0xFF;

        // Held Items?
        int item1 = FileFunctions.read2ByteInt(stats, Gen6Constants.bsCommonHeldItemOffset);
        int item2 = FileFunctions.read2ByteInt(stats, Gen6Constants.bsRareHeldItemOffset);

        if (item1 == item2) {
            // guaranteed
            pkmn.guaranteedHeldItem = item1;
            pkmn.commonHeldItem = 0;
            pkmn.rareHeldItem = 0;
            pkmn.darkGrassHeldItem = 0;
        } else {
            pkmn.guaranteedHeldItem = 0;
            pkmn.commonHeldItem = item1;
            pkmn.rareHeldItem = item2;
            pkmn.darkGrassHeldItem = FileFunctions.read2ByteInt(stats, Gen6Constants.bsDarkGrassHeldItemOffset);
        }

        int formeCount = stats[Gen6Constants.bsFormeCountOffset] & 0xFF;
        if (formeCount > 1) {
            if (!altFormes.keySet().contains(pkmn.number)) {
                int firstFormeOffset = FileFunctions.read2ByteInt(stats, Gen6Constants.bsFormeOffset);
                if (firstFormeOffset != 0) {
                    for (int i = 1; i < formeCount; i++) {
                        altFormes.put(firstFormeOffset + i - 1,new FormeInfo(pkmn.number,i,FileFunctions.read2ByteInt(stats,Gen6Constants.bsFormeSpriteOffset))); // Assumes that formes are in memory in the same order as their numbers
                        if (Gen6Constants.actuallyCosmeticForms.contains(firstFormeOffset+i-1)) {
                            if (pkmn.number != 421) { // No Cherrim
                                pkmn.cosmeticForms += 1;
                            }
                        }
                    }
                } else {
                    if (pkmn.number != 493 && pkmn.number != 649 && pkmn.number != 716) {
                        // Reason for exclusions:
                        // Arceus/Genesect: to avoid confusion
                        // Xerneas: Should be handled automatically?
                        pkmn.cosmeticForms = formeCount;
                    }
                }
            } else {
                if (Gen6Constants.actuallyCosmeticForms.contains(pkmn.number)) {
                    pkmn.actuallyCosmetic = true;
                }
            }
        }
    }

    private String[] readPokemonNames() {
        String[] pokeNames = new String[Gen6Constants.pokemonCount + 1];
        List<String> nameList = getStrings(false, romEntry.getInt("PokemonNamesTextOffset"));
        for (int i = 1; i <= Gen6Constants.pokemonCount; i++) {
            pokeNames[i] = nameList.get(i);
        }
        return pokeNames;
    }

    private void populateEvolutions() {
        for (Pokemon pkmn : pokes) {
            if (pkmn != null) {
                pkmn.evolutionsFrom.clear();
                pkmn.evolutionsTo.clear();
            }
        }

        // Read GARC
        try {
            GARCArchive evoGARC = readGARC(romEntry.getString("PokemonEvolutions"),true);
            for (int i = 1; i <= Gen6Constants.pokemonCount + Gen6Constants.getFormeCount(romEntry.romType); i++) {
                Pokemon pk = pokes[i];
                byte[] evoEntry = evoGARC.files.get(i).get(0);
                for (int evo = 0; evo < 8; evo++) {
                    int method = readWord(evoEntry, evo * 6);
                    int species = readWord(evoEntry, evo * 6 + 4);
                    if (method >= 1 && method <= Gen6Constants.evolutionMethodCount && species >= 1) {
                        EvolutionType et = EvolutionType.fromIndex(6, method);
                        if (et.equals(EvolutionType.LEVEL_HIGH_BEAUTY)) continue; // Remove Feebas "split" evolution
                        int extraInfo = readWord(evoEntry, evo * 6 + 2);
                        Evolution evol = new Evolution(pk, pokes[species], true, et, extraInfo);
                        if (!pk.evolutionsFrom.contains(evol)) {
                            pk.evolutionsFrom.add(evol);
                            pokes[species].evolutionsTo.add(evol);
                        }
                    }
                }
                // split evos don't carry stats
                if (pk.evolutionsFrom.size() > 1) {
                    for (Evolution e : pk.evolutionsFrom) {
                        e.carryStats = false;
                    }
                }
            }
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
    }

    private void populateMegaEvolutions() {
        for (Pokemon pkmn : pokes) {
            if (pkmn != null) {
                pkmn.megaEvolutionsFrom.clear();
                pkmn.megaEvolutionsTo.clear();
            }
        }

        // Read GARC
        try {
            megaEvolutions = new ArrayList<>();
            GARCArchive megaEvoGARC = readGARC(romEntry.getString("MegaEvolutions"),true);
            for (int i = 1; i <= Gen6Constants.pokemonCount; i++) {
                Pokemon pk = pokes[i];
                byte[] megaEvoEntry = megaEvoGARC.files.get(i).get(0);
                for (int evo = 0; evo < 3; evo++) {
                    int formNum = readWord(megaEvoEntry, evo * 8);
                    int method = readWord(megaEvoEntry, evo * 8 + 2);
                    if (method >= 1) {
                        int argument = readWord(megaEvoEntry, evo * 8 + 4);
                        int megaSpecies = absolutePokeNumByBaseForme
                                .getOrDefault(pk.number,dummyAbsolutePokeNums)
                                .getOrDefault(formNum,0);
                        MegaEvolution megaEvo = new MegaEvolution(pk, pokes[megaSpecies], method, argument);
                        if (!pk.megaEvolutionsFrom.contains(megaEvo)) {
                            pk.megaEvolutionsFrom.add(megaEvo);
                            pokes[megaSpecies].megaEvolutionsTo.add(megaEvo);
                        }
                        megaEvolutions.add(megaEvo);
                    }
                }
                // split evos don't carry stats
                if (pk.megaEvolutionsFrom.size() > 1) {
                    for (MegaEvolution e : pk.megaEvolutionsFrom) {
                        e.carryStats = false;
                    }
                }
            }
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
    }

    private List<String> getStrings(boolean isStoryText, int index) {
        GARCArchive baseGARC = isStoryText ? storyTextGarc : stringsGarc;
        byte[] rawFile = baseGARC.files.get(index).get(0);
        return new ArrayList<>(N3DSTxtHandler.readTexts(rawFile,true,romEntry.romType));
    }

    private void setStrings(boolean isStoryText, int index, List<String> strings) {
        GARCArchive baseGARC = isStoryText ? storyTextGarc : stringsGarc;
        byte[] oldRawFile = baseGARC.files.get(index).get(0);
        try {
            byte[] newRawFile = N3DSTxtHandler.saveEntry(oldRawFile, strings, romEntry.romType);
            baseGARC.setFile(index, newRawFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadMoves() {
        try {
            moveGarc = this.readGARC(romEntry.getString("MoveData"),true);
            int moveCount = Gen6Constants.getMoveCount(romEntry.romType);
            moves = new Move[moveCount + 1];
            List<String> moveNames = getStrings(false, romEntry.getInt("MoveNamesTextOffset"));
            for (int i = 1; i <= moveCount; i++) {
                byte[] moveData;
                if (romEntry.romType == Gen6Constants.Type_ORAS) {
                    moveData = Mini.UnpackMini(moveGarc.files.get(0).get(0), "WD")[i];
                } else {
                    moveData = moveGarc.files.get(i).get(0);
                }
                moves[i] = new Move();
                moves[i].name = moveNames.get(i);
                moves[i].number = i;
                moves[i].internalId = i;
                moves[i].hitratio = (moveData[4] & 0xFF);
                moves[i].power = moveData[3] & 0xFF;
                moves[i].pp = moveData[5] & 0xFF;
                moves[i].type = Gen6Constants.typeTable[moveData[0] & 0xFF];
                moves[i].category = Gen6Constants.moveCategoryIndices[moveData[2] & 0xFF];

                if (GlobalConstants.normalMultihitMoves.contains(i)) {
                    moves[i].hitCount = 19 / 6.0;
                } else if (GlobalConstants.doubleHitMoves.contains(i)) {
                    moves[i].hitCount = 2;
                } else if (i == GlobalConstants.TRIPLE_KICK_INDEX) {
                    moves[i].hitCount = 2.71; // this assumes the first hit lands
                }
            }
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
    }

    @Override
    protected void savingROM() throws IOException {
        savePokemonStats();
        saveMoves();
        try {
            writeCode(code);
            writeGARC(romEntry.getString("TextStrings"), stringsGarc);
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
    }

    private void savePokemonStats() {
        int k = 0x40;
        byte[] duplicateData = pokeGarc.files.get(Gen6Constants.pokemonCount + Gen6Constants.getFormeCount(romEntry.romType) + 1).get(0);
        for (int i = 1; i <= Gen6Constants.pokemonCount + Gen6Constants.getFormeCount(romEntry.romType); i++) {
            byte[] pokeData = pokeGarc.files.get(i).get(0);
            saveBasicPokeStats(pokes[i], pokeData);
            for (byte pokeDataByte : pokeData) {
                duplicateData[k] = pokeDataByte;
                k++;
            }
        }

        try {
            this.writeGARC(romEntry.getString("PokemonStats"),pokeGarc);
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }

        writeEvolutions();
    }

    private void saveBasicPokeStats(Pokemon pkmn, byte[] stats) {
        stats[Gen6Constants.bsHPOffset] = (byte) pkmn.hp;
        stats[Gen6Constants.bsAttackOffset] = (byte) pkmn.attack;
        stats[Gen6Constants.bsDefenseOffset] = (byte) pkmn.defense;
        stats[Gen6Constants.bsSpeedOffset] = (byte) pkmn.speed;
        stats[Gen6Constants.bsSpAtkOffset] = (byte) pkmn.spatk;
        stats[Gen6Constants.bsSpDefOffset] = (byte) pkmn.spdef;
        stats[Gen6Constants.bsPrimaryTypeOffset] = Gen6Constants.typeToByte(pkmn.primaryType);
        if (pkmn.secondaryType == null) {
            stats[Gen6Constants.bsSecondaryTypeOffset] = stats[Gen6Constants.bsPrimaryTypeOffset];
        } else {
            stats[Gen6Constants.bsSecondaryTypeOffset] = Gen6Constants.typeToByte(pkmn.secondaryType);
        }
        stats[Gen6Constants.bsCatchRateOffset] = (byte) pkmn.catchRate;
        stats[Gen6Constants.bsGrowthCurveOffset] = pkmn.growthCurve.toByte();

        stats[Gen6Constants.bsAbility1Offset] = (byte) pkmn.ability1;
        stats[Gen6Constants.bsAbility2Offset] = pkmn.ability2 != 0 ? (byte) pkmn.ability2 : (byte) pkmn.ability1;
        stats[Gen6Constants.bsAbility3Offset] = (byte) pkmn.ability3;

        // Held items
        if (pkmn.guaranteedHeldItem > 0) {
            FileFunctions.write2ByteInt(stats, Gen6Constants.bsCommonHeldItemOffset, pkmn.guaranteedHeldItem);
            FileFunctions.write2ByteInt(stats, Gen6Constants.bsRareHeldItemOffset, pkmn.guaranteedHeldItem);
            FileFunctions.write2ByteInt(stats, Gen6Constants.bsDarkGrassHeldItemOffset, 0);
        } else {
            FileFunctions.write2ByteInt(stats, Gen6Constants.bsCommonHeldItemOffset, pkmn.commonHeldItem);
            FileFunctions.write2ByteInt(stats, Gen6Constants.bsRareHeldItemOffset, pkmn.rareHeldItem);
            FileFunctions.write2ByteInt(stats, Gen6Constants.bsDarkGrassHeldItemOffset, pkmn.darkGrassHeldItem);
        }
    }

    private void writeEvolutions() {
        try {
            GARCArchive evoGARC = readGARC(romEntry.getString("PokemonEvolutions"),true);
            for (int i = 1; i <= Gen6Constants.pokemonCount; i++) {
                byte[] evoEntry = evoGARC.files.get(i).get(0);
                Pokemon pk = pokes[i];
                int evosWritten = 0;
                for (Evolution evo : pk.evolutionsFrom) {
                    writeWord(evoEntry, evosWritten * 6, evo.type.toIndex(5));
                    writeWord(evoEntry, evosWritten * 6 + 2, evo.extraInfo);
                    writeWord(evoEntry, evosWritten * 6 + 4, evo.to.number);
                    evosWritten++;
                    if (evosWritten == 7) {
                        break;
                    }
                }
                while (evosWritten < 7) {
                    writeWord(evoEntry, evosWritten * 6, 0);
                    writeWord(evoEntry, evosWritten * 6 + 2, 0);
                    writeWord(evoEntry, evosWritten * 6 + 4, 0);
                    evosWritten++;
                }
            }
            writeGARC(romEntry.getString("PokemonEvolutions"), evoGARC);
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
    }

    private void saveMoves() {
        int moveCount = Gen6Constants.getMoveCount(romEntry.romType);
        byte[][] miniArchive = new byte[0][0];
        if (romEntry.romType == Gen6Constants.Type_ORAS) {
            miniArchive = Mini.UnpackMini(moveGarc.files.get(0).get(0), "WD");
        }
        for (int i = 1; i <= moveCount; i++) {
            byte[] data;
            if (romEntry.romType == Gen6Constants.Type_ORAS) {
                data = miniArchive[i];
            } else {
                data = moveGarc.files.get(i).get(0);
            }
            data[2] = Gen6Constants.moveCategoryToByte(moves[i].category);
            data[3] = (byte) moves[i].power;
            data[0] = Gen6Constants.typeToByte(moves[i].type);
            int hitratio = (int) Math.round(moves[i].hitratio);
            if (hitratio < 0) {
                hitratio = 0;
            }
            if (hitratio > 101) {
                hitratio = 100;
            }
            data[4] = (byte) hitratio;
            data[5] = (byte) moves[i].pp;
        }
        try {
            if (romEntry.romType == Gen6Constants.Type_ORAS) {
                moveGarc.setFile(0, Mini.PackMini(miniArchive, "WD"));
            }
            this.writeGARC(romEntry.getString("MoveData"), moveGarc);
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
    }

    @Override
    public List<Pokemon> getPokemon() {
        return pokemonList;
    }

    @Override
    public List<Pokemon> getPokemonInclFormes() {
        return pokemonListInclFormes;
    }

    @Override
    public List<Pokemon> getAltFormes() {
        return new ArrayList<>();
    }

    @Override
    public List<MegaEvolution> getMegaEvolutions() {
        return megaEvolutions;
    }

    @Override
    public List<Pokemon> getStarters() {
        List<StaticEncounter> starters = new ArrayList<>();
        try {
            byte[] fieldCRO = readFile(romEntry.getString("StaticPokemon"));

            List<Integer> starterIndices =
                    Arrays.stream(romEntry.arrayEntries.get("StarterIndices")).boxed().collect(Collectors.toList());

            // Gift Pokemon
            int count = Gen6Constants.getGiftPokemonCount(romEntry.romType);
            int size = Gen6Constants.getGiftPokemonSize(romEntry.romType);
            int offset = romEntry.getInt("GiftPokemonOffset");
            for (int i = 0; i < count; i++) {
                if (!starterIndices.contains(i)) continue;
                StaticEncounter se = new StaticEncounter();
                se.pkmn = pokes[FileFunctions.read2ByteInt(fieldCRO,offset+i*size)];
                se.forme = fieldCRO[offset+i*size + 4];
                se.level = fieldCRO[offset+i*size + 5];
                int heldItem = FileFunctions.readFullIntLittleEndian(fieldCRO,offset+i*size + 12);
                if (heldItem < 0) {
                    heldItem = 0;
                }
                se.heldItem = heldItem;
                starters.add(se);
            }
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }

        return starters.stream().map(pk -> pk.pkmn).collect(Collectors.toList());
    }

    @Override
    public boolean setStarters(List<Pokemon> newStarters) {
        try {
            byte[] fieldCRO = readFile(romEntry.getString("StaticPokemon"));
            byte[] displayCRO = readFile(romEntry.getString("StarterDisplay"));

            List<Integer> starterIndices =
                    Arrays.stream(romEntry.arrayEntries.get("StarterIndices")).boxed().collect(Collectors.toList());

            // Gift Pokemon
            int count = Gen6Constants.getGiftPokemonCount(romEntry.romType);
            int size = Gen6Constants.getGiftPokemonSize(romEntry.romType);
            int offset = romEntry.getInt("GiftPokemonOffset");
            int displayOffset = readWord(displayCRO,romEntry.getInt("StarterOffsetOffset")) + romEntry.getInt("StarterExtraOffset");

            Iterator<Pokemon> starterIter = newStarters.iterator();

            int displayIndex = 0;

            List<String> starterText = getStrings(false,romEntry.getInt("StarterTextOffset"));
            int[] starterTextIndices = romEntry.arrayEntries.get("SpecificStarterTextOffsets");

            for (int i = 0; i < count; i++) {
                if (!starterIndices.contains(i)) continue;

                StaticEncounter newStatic = new StaticEncounter();
                Pokemon starter = starterIter.next();
                if (starter.formeNumber > 0) {
                    newStatic.forme = starter.formeNumber;
                    newStatic.formeSuffix = starter.formeSuffix;
                    starter = mainPokemonList.get(starter.baseForme.number - 1);
                }
                newStatic.pkmn = starter;
                if (starter.cosmeticForms > 0) {
                    newStatic.forme = this.random.nextInt(starter.cosmeticForms);
                }
                writeWord(fieldCRO,offset+i*size,newStatic.pkmn.number);
                fieldCRO[offset+i*size + 4] = (byte)newStatic.forme;
//                fieldCRO[offset+i*size + 5] = (byte)newStatic.level;
                if (newStatic.heldItem == 0) {
                    writeWord(fieldCRO,offset+i*size + 12,-1);
                } else {
                    writeWord(fieldCRO,offset+i*size + 12,newStatic.heldItem);
                }
                writeWord(displayCRO,displayOffset+displayIndex*0x54,newStatic.pkmn.number);
                displayCRO[displayOffset+displayIndex*0x54+2] = (byte)newStatic.forme;
                if (displayIndex < 3) {
                    starterText.set(starterTextIndices[displayIndex],
                            "The " + starter.primaryType.camelCase() + "-type Pokémon\n[VAR PKNAME(0000)]");
                }
                displayIndex++;
            }
            writeFile(romEntry.getString("StaticPokemon"),fieldCRO);
            writeFile(romEntry.getString("StarterDisplay"),displayCRO);
            setStrings(false, romEntry.getInt("StarterTextOffset"), starterText);
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
        return true;
    }

    @Override
    public int starterCount() {
        return romEntry.romType == Gen6Constants.Type_XY ? 6 : 12;
    }

    @Override
    public List<Integer> getStarterHeldItems() {
        return new ArrayList<>();
    }

    @Override
    public void setStarterHeldItems(List<Integer> items) {
        // do nothing for now
    }

    @Override
    public List<Move> getMoves() {
        return Arrays.asList(moves);
    }

    @Override
    public List<EncounterSet> getEncounters(boolean useTimeOfDay) {
        if (!loadedWildMapNames) {
            loadWildMapNames();
        }
        try {
            if (romEntry.romType == Gen6Constants.Type_ORAS) {
                return getEncountersORAS();
            } else {
                return getEncountersXY();
            }
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
    }

    private List<EncounterSet> getEncountersXY() throws IOException {
        GARCArchive encounterGarc = readGARC(romEntry.getString("WildPokemon"), false);
        List<EncounterSet> encounters = new ArrayList<>();
        for (int i = 0; i < encounterGarc.files.size() - 1; i++) {
            byte[] b = encounterGarc.files.get(i).get(0);
            if (!wildMapNames.containsKey(i)) {
                wildMapNames.put(i, "? Unknown ?");
            }
            String mapName = wildMapNames.get(i);
            int offset = FileFunctions.readFullIntLittleEndian(b, 0x10) + 0x10;
            int length = b.length - offset;
            if (length < 0x178) { // No encounters in this map
                continue;
            }
            byte[] encounterData = new byte[0x178];
            System.arraycopy(b, offset, encounterData, 0, 0x178);

            // TODO: Is there some rate we can check like in older gens?
            // First, 12 grass encounters, 12 rough terrain encounters, and 12 encounters each for yellow/purple/red flowers
            EncounterSet grassEncounters = readEncounter(encounterData, 0, 12);
            if (grassEncounters.encounters.size() > 0) {
                grassEncounters.displayName = mapName + " Grass/Cave";
                encounters.add(grassEncounters);
            }
            EncounterSet yellowFlowerEncounters = readEncounter(encounterData, 48, 12);
            if (yellowFlowerEncounters.encounters.size() > 0) {
                yellowFlowerEncounters.displayName = mapName + " Yellow Flowers";
                encounters.add(yellowFlowerEncounters);
            }
            EncounterSet purpleFlowerEncounters = readEncounter(encounterData, 96, 12);
            if (purpleFlowerEncounters.encounters.size() > 0) {
                purpleFlowerEncounters.displayName = mapName + " Purple Flowers";
                encounters.add(purpleFlowerEncounters);
            }
            EncounterSet redFlowerEncounters = readEncounter(encounterData, 144, 12);
            if (redFlowerEncounters.encounters.size() > 0) {
                redFlowerEncounters.displayName = mapName + " Red Flowers";
                encounters.add(redFlowerEncounters);
            }
            EncounterSet roughTerrainEncounters = readEncounter(encounterData, 192, 12);
            if (roughTerrainEncounters.encounters.size() > 0) {
                roughTerrainEncounters.displayName = mapName + " Rough Terrain";
                encounters.add(roughTerrainEncounters);
            }

            // 5 surf and 5 rock smash encounters
            EncounterSet surfEncounters = readEncounter(encounterData, 240, 5);
            if (surfEncounters.encounters.size() > 0) {
                surfEncounters.displayName = mapName + " Surf";
                encounters.add(surfEncounters);
            }
            EncounterSet rockSmashEncounters = readEncounter(encounterData, 260, 5);
            if (rockSmashEncounters.encounters.size() > 0) {
                rockSmashEncounters.displayName = mapName + " Rock Smash";
                encounters.add(rockSmashEncounters);
            }

            // 3 Encounters for each type of rod
            EncounterSet oldRodEncounters = readEncounter(encounterData, 280, 3);
            if (oldRodEncounters.encounters.size() > 0) {
                oldRodEncounters.displayName = mapName + " Old Rod";
                encounters.add(oldRodEncounters);
            }
            EncounterSet goodRodEncounters = readEncounter(encounterData, 292, 3);
            if (goodRodEncounters.encounters.size() > 0) {
                goodRodEncounters.displayName = mapName + " Good Rod";
                encounters.add(goodRodEncounters);
            }
            EncounterSet superRodEncounters = readEncounter(encounterData, 304, 3);
            if (superRodEncounters.encounters.size() > 0) {
                superRodEncounters.displayName = mapName + " Super Rod";
                encounters.add(superRodEncounters);
            }

            // Lastly, 5 for each kind of Horde
            EncounterSet hordeCommonEncounters = readEncounter(encounterData, 316, 5);
            if (hordeCommonEncounters.encounters.size() > 0) {
                hordeCommonEncounters.displayName = mapName + " Common Horde";
                encounters.add(hordeCommonEncounters);
            }
            EncounterSet hordeUncommonEncounters = readEncounter(encounterData, 336, 5);
            if (hordeUncommonEncounters.encounters.size() > 0) {
                hordeUncommonEncounters.displayName = mapName + " Uncommon Horde";
                encounters.add(hordeUncommonEncounters);
            }
            EncounterSet hordeRareEncounters = readEncounter(encounterData, 356, 5);
            if (hordeRareEncounters.encounters.size() > 0) {
                hordeRareEncounters.displayName = mapName + " Rare Horde";
                encounters.add(hordeRareEncounters);
            }
        }
        return encounters;
    }

    private List<EncounterSet> getEncountersORAS() throws IOException {
        GARCArchive encounterGarc = readGARC(romEntry.getString("WildPokemon"), false);
        List<EncounterSet> encounters = new ArrayList<>();
        for (int i = 0; i < encounterGarc.files.size() - 2; i++) {
            byte[] b = encounterGarc.files.get(i).get(0);
            if (!wildMapNames.containsKey(i)) {
                wildMapNames.put(i, "? Unknown ?");
            }
            String mapName = wildMapNames.get(i);
            int offset = FileFunctions.readFullIntLittleEndian(b, 0x10) + 0xE;
            int offset2 = FileFunctions.readFullIntLittleEndian(b, 0x14);
            int length = offset2 - offset;
            if (length < 0xF6) { // No encounters in this map
                continue;
            }
            byte[] encounterData = new byte[0xF6];
            System.arraycopy(b, offset, encounterData, 0, 0xF6);

            // First, read 12 grass encounters and 12 long grass encounters
            EncounterSet grassEncounters = readEncounter(encounterData, 0, 12);
            if (grassEncounters.encounters.size() > 0) {
                grassEncounters.displayName = mapName + " Grass/Cave";
                encounters.add(grassEncounters);
            }
            EncounterSet longGrassEncounters = readEncounter(encounterData, 48, 12);
            if (longGrassEncounters.encounters.size() > 0) {
                longGrassEncounters.displayName = mapName + " Long Grass";
                encounters.add(longGrassEncounters);
            }

            // Now, 3 DexNav Foreign encounters
            EncounterSet dexNavForeignEncounters = readEncounter(encounterData, 96, 3);
            if (dexNavForeignEncounters.encounters.size() > 0) {
                dexNavForeignEncounters.displayName = mapName + " DexNav Foreign Encounter";
                encounters.add(dexNavForeignEncounters);
            }

            // 5 surf and 5 rock smash encounters
            EncounterSet surfEncounters = readEncounter(encounterData, 108, 5);
            if (surfEncounters.encounters.size() > 0) {
                surfEncounters.displayName = mapName + " Surf";
                encounters.add(surfEncounters);
            }
            EncounterSet rockSmashEncounters = readEncounter(encounterData, 128, 5);
            if (rockSmashEncounters.encounters.size() > 0) {
                rockSmashEncounters.displayName = mapName + " Rock Smash";
                encounters.add(rockSmashEncounters);
            }

            // 3 Encounters for each type of rod
            EncounterSet oldRodEncounters = readEncounter(encounterData, 148, 3);
            if (oldRodEncounters.encounters.size() > 0) {
                oldRodEncounters.displayName = mapName + " Old Rod";
                encounters.add(oldRodEncounters);
            }
            EncounterSet goodRodEncounters = readEncounter(encounterData, 160, 3);
            if (goodRodEncounters.encounters.size() > 0) {
                goodRodEncounters.displayName = mapName + " Good Rod";
                encounters.add(goodRodEncounters);
            }
            EncounterSet superRodEncounters = readEncounter(encounterData, 172, 3);
            if (superRodEncounters.encounters.size() > 0) {
                superRodEncounters.displayName = mapName + " Super Rod";
                encounters.add(superRodEncounters);
            }

            // Lastly, 5 for each kind of Horde
            EncounterSet hordeCommonEncounters = readEncounter(encounterData, 184, 5);
            if (hordeCommonEncounters.encounters.size() > 0) {
                hordeCommonEncounters.displayName = mapName + " Common Horde";
                encounters.add(hordeCommonEncounters);
            }
            EncounterSet hordeUncommonEncounters = readEncounter(encounterData, 204, 5);
            if (hordeUncommonEncounters.encounters.size() > 0) {
                hordeUncommonEncounters.displayName = mapName + " Uncommon Horde";
                encounters.add(hordeUncommonEncounters);
            }
            EncounterSet hordeRareEncounters = readEncounter(encounterData, 224, 5);
            if (hordeRareEncounters.encounters.size() > 0) {
                hordeRareEncounters.displayName = mapName + " Rare Horde";
                encounters.add(hordeRareEncounters);
            }
        }
        return encounters;
    }

    private EncounterSet readEncounter(byte[] data, int offset, int amount) {
        EncounterSet es = new EncounterSet();
        es.rate = 1;
        for (int i = 0; i < amount; i++) {
            int species = readWord(data, offset + i * 4) & 0x7FF;
            int forme = readWord(data, offset + i * 4) >> 11;
            if (species != 0) {
                Encounter e = new Encounter();
                Pokemon baseForme = pokes[species];

                // If the forme is purely cosmetic, just use the base forme as the Pokemon
                // for this encounter (the cosmetic forme will be stored in the encounter).
                // Do the same for formes 30 and 31, because they actually aren't formes, but
                // rather act as indicators for what forme should appear when encountered:
                // 30 = Spawn the cosmetic forme specific to the user's region (Scatterbug line)
                // 31 = Spawn *any* cosmetic forme with equal probability (Unown Mirage Cave)
                if (forme <= baseForme.cosmeticForms || forme == 30 || forme == 31) {
                    e.pokemon = pokes[species];
                } else {
                    int speciesWithForme = absolutePokeNumByBaseForme
                            .getOrDefault(species, dummyAbsolutePokeNums)
                            .getOrDefault(forme, 0);
                    e.pokemon = pokes[speciesWithForme];
                }
                e.formeNumber = forme;
                e.level = data[offset + 2 + i * 4];
                e.maxLevel = data[offset + 3 + i * 4];
                es.encounters.add(e);
            }
        }
        return es;
    }

    @Override
    public void setEncounters(boolean useTimeOfDay, List<EncounterSet> encountersList) {
        try {
            if (romEntry.romType == Gen6Constants.Type_ORAS) {
                setEncountersORAS(encountersList);
            } else {
                setEncountersXY(encountersList);
            }
        } catch (IOException ex) {
            throw new RandomizerIOException(ex);
        }
    }

    private void setEncountersXY(List<EncounterSet> encountersList) throws IOException {
        String encountersFile = romEntry.getString("WildPokemon");
        GARCArchive encounterGarc = readGARC(encountersFile, false);
        Iterator<EncounterSet> encounters = encountersList.iterator();
        for (int i = 0; i < encounterGarc.files.size() - 1; i++) {
            byte[] b = encounterGarc.files.get(i).get(0);
            int offset = FileFunctions.readFullIntLittleEndian(b, 0x10) + 0x10;
            int length = b.length - offset;
            if (length < 0x178) { // No encounters in this map
                continue;
            }
            byte[] encounterData = new byte[0x178];
            System.arraycopy(b, offset, encounterData, 0, 0x178);

            // First, 12 grass encounters, 12 rough terrain encounters, and 12 encounters each for yellow/purple/red flowers
            if (readEncounter(encounterData, 0, 12).encounters.size() > 0) {
                EncounterSet grass = encounters.next();
                writeEncounter(encounterData, 0, grass.encounters);
            }
            if (readEncounter(encounterData, 48, 12).encounters.size() > 0) {
                EncounterSet yellowFlowers = encounters.next();
                writeEncounter(encounterData, 48, yellowFlowers.encounters);
            }
            if (readEncounter(encounterData, 96, 12).encounters.size() > 0) {
                EncounterSet purpleFlowers = encounters.next();
                writeEncounter(encounterData, 96, purpleFlowers.encounters);
            }
            if (readEncounter(encounterData, 144, 12).encounters.size() > 0) {
                EncounterSet redFlowers = encounters.next();
                writeEncounter(encounterData, 144, redFlowers.encounters);
            }
            if (readEncounter(encounterData, 192, 12).encounters.size() > 0) {
                EncounterSet roughTerrain = encounters.next();
                writeEncounter(encounterData, 192, roughTerrain.encounters);
            }

            // 5 surf and 5 rock smash encounters
            if (readEncounter(encounterData, 240, 5).encounters.size() > 0) {
                EncounterSet surf = encounters.next();
                writeEncounter(encounterData, 240, surf.encounters);
            }
            if (readEncounter(encounterData, 260, 5).encounters.size() > 0) {
                EncounterSet rockSmash = encounters.next();
                writeEncounter(encounterData, 260, rockSmash.encounters);
            }

            // 3 Encounters for each type of rod
            if (readEncounter(encounterData, 280, 3).encounters.size() > 0) {
                EncounterSet oldRod = encounters.next();
                writeEncounter(encounterData, 280, oldRod.encounters);
            }
            if (readEncounter(encounterData, 292, 3).encounters.size() > 0) {
                EncounterSet goodRod = encounters.next();
                writeEncounter(encounterData, 292, goodRod.encounters);
            }
            if (readEncounter(encounterData, 304, 3).encounters.size() > 0) {
                EncounterSet superRod = encounters.next();
                writeEncounter(encounterData, 304, superRod.encounters);
            }

            // Lastly, 5 for each kind of Horde
            if (readEncounter(encounterData, 316, 5).encounters.size() > 0) {
                EncounterSet commonHorde = encounters.next();
                writeEncounter(encounterData, 316, commonHorde.encounters);
            }
            if (readEncounter(encounterData, 336, 5).encounters.size() > 0) {
                EncounterSet uncommonHorde = encounters.next();
                writeEncounter(encounterData, 336, uncommonHorde.encounters);
            }
            if (readEncounter(encounterData, 356, 5).encounters.size() > 0) {
                EncounterSet rareHorde = encounters.next();
                writeEncounter(encounterData, 356, rareHorde.encounters);
            }

            // Write the encounter data back to the file
            System.arraycopy(encounterData, 0, b, offset, 0x178);
        }

        // Save
        // TODO: Needs compression, game crashes on load without it
        writeGARC(encountersFile, encounterGarc);
    }

    private void setEncountersORAS(List<EncounterSet> encountersList) throws IOException {
        String encountersFile = romEntry.getString("WildPokemon");
        GARCArchive encounterGarc = readGARC(encountersFile, false);
        Iterator<EncounterSet> encounters = encountersList.iterator();
        byte[] decStorage = encounterGarc.files.get(encounterGarc.files.size() - 1).get(0);
        for (int i = 0; i < encounterGarc.files.size() - 2; i++) {
            byte[] b = encounterGarc.files.get(i).get(0);
            int offset = FileFunctions.readFullIntLittleEndian(b, 0x10) + 0xE;
            int offset2 = FileFunctions.readFullIntLittleEndian(b, 0x14);
            int length = offset2 - offset;
            if (length < 0xF6) { // No encounters in this map
                continue;
            }
            byte[] encounterData = new byte[0xF6];
            System.arraycopy(b, offset, encounterData, 0, 0xF6);

            // First, 12 grass encounters and 12 long grass encounters
            if (readEncounter(encounterData, 0, 12).encounters.size() > 0) {
                EncounterSet grass = encounters.next();
                writeEncounter(encounterData, 0, grass.encounters);
            }
            if (readEncounter(encounterData, 48, 12).encounters.size() > 0) {
                EncounterSet longGrass = encounters.next();
                writeEncounter(encounterData, 48, longGrass.encounters);
            }

            // Now, 3 DexNav Foreign encounters
            if (readEncounter(encounterData, 96, 3).encounters.size() > 0) {
                EncounterSet dexNav = encounters.next();
                writeEncounter(encounterData, 96, dexNav.encounters);
            }

            // 5 surf and 5 rock smash encounters
            if (readEncounter(encounterData, 108, 5).encounters.size() > 0) {
                EncounterSet surf = encounters.next();
                writeEncounter(encounterData, 108, surf.encounters);
            }
            if (readEncounter(encounterData, 128, 5).encounters.size() > 0) {
                EncounterSet rockSmash = encounters.next();
                writeEncounter(encounterData, 128, rockSmash.encounters);
            }

            // 3 Encounters for each type of rod
            if (readEncounter(encounterData, 148, 3).encounters.size() > 0) {
                EncounterSet oldRod = encounters.next();
                writeEncounter(encounterData, 148, oldRod.encounters);
            }
            if (readEncounter(encounterData, 160, 3).encounters.size() > 0) {
                EncounterSet goodRod = encounters.next();
                writeEncounter(encounterData, 160, goodRod.encounters);
            }
            if (readEncounter(encounterData, 172, 3).encounters.size() > 0) {
                EncounterSet superRod = encounters.next();
                writeEncounter(encounterData, 172, superRod.encounters);
            }

            // Lastly, 5 for each kind of Horde
            if (readEncounter(encounterData, 184, 5).encounters.size() > 0) {
                EncounterSet commonHorde = encounters.next();
                writeEncounter(encounterData, 184, commonHorde.encounters);
            }
            if (readEncounter(encounterData, 204, 5).encounters.size() > 0) {
                EncounterSet uncommonHorde = encounters.next();
                writeEncounter(encounterData, 204, uncommonHorde.encounters);
            }
            if (readEncounter(encounterData, 224, 5).encounters.size() > 0) {
                EncounterSet rareHorde = encounters.next();
                writeEncounter(encounterData, 224, rareHorde.encounters);
            }

            // Write the encounter data back to the file
            System.arraycopy(encounterData, 0, b, offset, 0xF6);

            // Also write the encounter data to the decStorage file
            int decStorageOffset = FileFunctions.readFullIntLittleEndian(decStorage, (i + 1) * 4) + 0xE;
            System.arraycopy(encounterData, 0, decStorage, decStorageOffset, 0xF4);
        }

        // Save
        // TODO: Needs compression, game crashes on load without it
        writeGARC(encountersFile, encounterGarc);
    }

    private void writeEncounter(byte[] data, int offset, List<Encounter> encounters) {
        for (int i = 0; i < encounters.size(); i++) {
            Encounter encounter = encounters.get(i);
            if (encounter.pokemon.formeNumber > 0) { // Failsafe if we need to write encounters without modifying species
                encounter.pokemon = encounter.pokemon.baseForme;
            }
            int speciesAndFormeData = (encounter.formeNumber << 11) + encounter.pokemon.number;
            writeWord(data, offset + i * 4, speciesAndFormeData);
            data[offset + 2 + i * 4] = (byte) encounter.level;
            data[offset + 3 + i * 4] = (byte) encounter.maxLevel;
        }
    }

    private void loadWildMapNames() {
        try {
            wildMapNames = new HashMap<>();
            GARCArchive encounterGarc = this.readGARC(romEntry.getString("WildPokemon"), false);
            int zoneDataOffset = romEntry.getInt("MapTableFileOffset");
            byte[] zoneData = encounterGarc.files.get(zoneDataOffset).get(0);
            List<String> allMapNames = getStrings(false, romEntry.getInt("MapNamesTextOffset"));
            for (int map = 0; map < zoneDataOffset; map++) {
                int indexNum = (map * 56) + 0x1C;
                int nameIndex1 = zoneData[indexNum] & 0xFF;
                int nameIndex2 = 0x100 * ((int) (zoneData[indexNum + 1]) & 1);
                String mapName = allMapNames.get(nameIndex1 + nameIndex2);
                wildMapNames.put(map, mapName);
            }
            loadedWildMapNames = true;
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
    }

    @Override
    public List<Trainer> getTrainers() {
        return new ArrayList<>();
    }

    @Override
    public List<Integer> getMainPlaythroughTrainers() {
        return new ArrayList<>();
    }

    @Override
    public List<Integer> getEvolutionItems() {
        return new ArrayList<>();
    }

    @Override
    public void setTrainers(List<Trainer> trainerData) {
        // do nothing for now
    }

    @Override
    public Map<Integer, List<MoveLearnt>> getMovesLearnt() {
        Map<Integer, List<MoveLearnt>> movesets = new TreeMap<>();
        try {
            GARCArchive movesLearnt = this.readGARC(romEntry.getString("PokemonMovesets"),true);
            int formeCount = Gen6Constants.getFormeCount(romEntry.romType);
//            int formeOffset = Gen5Constants.getFormeMovesetOffset(romEntry.romType);
            for (int i = 1; i <= Gen6Constants.pokemonCount + formeCount; i++) {
                Pokemon pkmn = pokes[i];
                byte[] movedata;
//                if (i > Gen6Constants.pokemonCount) {
//                    movedata = movesLearnt.files.get(i + formeOffset);
//                } else {
//                    movedata = movesLearnt.files.get(i);
//                }
                movedata = movesLearnt.files.get(i).get(0);
                int moveDataLoc = 0;
                List<MoveLearnt> learnt = new ArrayList<>();
                while (readWord(movedata, moveDataLoc) != 0xFFFF || readWord(movedata, moveDataLoc + 2) != 0xFFFF) {
                    int move = readWord(movedata, moveDataLoc);
                    int level = readWord(movedata, moveDataLoc + 2);
                    MoveLearnt ml = new MoveLearnt();
                    ml.level = level;
                    ml.move = move;
                    learnt.add(ml);
                    moveDataLoc += 4;
                }
                movesets.put(pkmn.number, learnt);
            }
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
        return movesets;
    }

    @Override
    public void setMovesLearnt(Map<Integer, List<MoveLearnt>> movesets) {
        try {
            GARCArchive movesLearnt = readGARC(romEntry.getString("PokemonMovesets"),true);
            int formeCount = Gen6Constants.getFormeCount(romEntry.romType);
//            int formeOffset = Gen6Constants.getFormeMovesetOffset(romEntry.romType);
            for (int i = 1; i <= Gen6Constants.pokemonCount + formeCount; i++) {
                Pokemon pkmn = pokes[i];
                List<MoveLearnt> learnt = movesets.get(pkmn.number);
                int sizeNeeded = learnt.size() * 4 + 4;
                byte[] moveset = new byte[sizeNeeded];
                int j = 0;
                for (; j < learnt.size(); j++) {
                    MoveLearnt ml = learnt.get(j);
                    writeWord(moveset, j * 4, ml.move);
                    writeWord(moveset, j * 4 + 2, ml.level);
                }
                writeWord(moveset, j * 4, 0xFFFF);
                writeWord(moveset, j * 4 + 2, 0xFFFF);
//                if (i > Gen5Constants.pokemonCount) {
//                    movesLearnt.files.set(i + formeOffset, moveset);
//                } else {
//                    movesLearnt.files.set(i, moveset);
//                }
                movesLearnt.setFile(i, moveset);
            }
            // Save
            this.writeGARC(romEntry.getString("PokemonMovesets"), movesLearnt);
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }

    }

    @Override
    public boolean canChangeStaticPokemon() {
        return romEntry.staticPokemonSupport;
    }

    @Override
    public List<StaticEncounter> getStaticPokemon() {
        List<StaticEncounter> statics = new ArrayList<>();
        try {
            byte[] fieldCRO = readFile(romEntry.getString("StaticPokemon"));

            // Static Pokemon
            int count = Gen6Constants.getStaticPokemonCount(romEntry.romType);
            int size = Gen6Constants.staticPokemonSize;
            int offset = romEntry.getInt("StaticPokemonOffset");
            for (int i = 0; i < count; i++) {
                StaticEncounter se = new StaticEncounter();
                se.pkmn = pokes[FileFunctions.read2ByteInt(fieldCRO,offset+i*size)];
                se.forme = fieldCRO[offset+i*size + 2];
                se.level = fieldCRO[offset+i*size + 3];
                short heldItem = (short)FileFunctions.read2ByteInt(fieldCRO,offset+i*size + 4);
                if (heldItem < 0) {
                    heldItem = 0;
                }
                se.heldItem = heldItem;
                se.formeSuffix = Gen6Constants.getFormeSuffixByBaseForme(se.pkmn.number,se.forme);
                statics.add(se);
            }

            List<Integer> skipStarters =
                    Arrays.stream(romEntry.arrayEntries.get("StarterIndices")).boxed().collect(Collectors.toList());

            // Gift Pokemon
            count = Gen6Constants.getGiftPokemonCount(romEntry.romType);
            size = Gen6Constants.getGiftPokemonSize(romEntry.romType);
            offset = romEntry.getInt("GiftPokemonOffset");
            for (int i = 0; i < count; i++) {
                if (skipStarters.contains(i)) continue;
                StaticEncounter se = new StaticEncounter();
                se.pkmn = pokes[FileFunctions.read2ByteInt(fieldCRO,offset+i*size)];
                se.forme = fieldCRO[offset+i*size + 4];
                se.level = fieldCRO[offset+i*size + 5];
                int heldItem = FileFunctions.readFullIntLittleEndian(fieldCRO,offset+i*size + 12);
                if (heldItem < 0) {
                    heldItem = 0;
                }
                se.heldItem = heldItem;
                statics.add(se);
            }
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }

        return statics;
    }

    @Override
    public boolean setStaticPokemon(List<StaticEncounter> staticPokemon) {
        // Static Pokemon
        try {
            byte[] fieldCRO = readFile(romEntry.getString("StaticPokemon"));

            Iterator<StaticEncounter> staticIter = staticPokemon.iterator();

            int staticCount = Gen6Constants.getStaticPokemonCount(romEntry.romType);
            int size = Gen6Constants.staticPokemonSize;
            int offset = romEntry.getInt("StaticPokemonOffset");
            for (int i = 0; i < staticCount; i++) {
                StaticEncounter se = staticIter.next();
                writeWord(fieldCRO,offset+i*size,se.pkmn.number);
                fieldCRO[offset+i*size + 2] = (byte)se.forme;
                fieldCRO[offset+i*size + 3] = (byte)se.level;
                if (se.heldItem == 0) {
                    writeWord(fieldCRO,offset+i*size + 4,-1);
                } else {
                    writeWord(fieldCRO,offset+i*size + 4,se.heldItem);
                }
            }

            List<Integer> skipStarters =
                    Arrays.stream(romEntry.arrayEntries.get("StarterIndices")).boxed().collect(Collectors.toList());

            // Gift Pokemon
            int giftCount = Gen6Constants.getGiftPokemonCount(romEntry.romType);
            size = Gen6Constants.getGiftPokemonSize(romEntry.romType);
            offset = romEntry.getInt("GiftPokemonOffset");
            for (int i = 0; i < giftCount; i++) {
                if (skipStarters.contains(i)) continue;
                StaticEncounter se = staticIter.next();
                writeWord(fieldCRO,offset+i*size,se.pkmn.number);
                fieldCRO[offset+i*size + 4] = (byte)se.forme;
                fieldCRO[offset+i*size + 5] = (byte)se.level;
                if (se.heldItem == 0) {
                    writeWord(fieldCRO,offset+i*size + 12,-1);
                } else {
                    writeWord(fieldCRO,offset+i*size + 12,se.heldItem);
                }
            }
            writeFile(romEntry.getString("StaticPokemon"),fieldCRO);
            return true;
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
    }

    @Override
    public int miscTweaksAvailable() {
        return 0;
    }

    @Override
    public void applyMiscTweak(MiscTweak tweak) {
        // do nothing for now
    }

    @Override
    public List<Integer> getTMMoves() {
        String tmDataPrefix = Gen6Constants.tmDataPrefix;
        int offset = find(code, tmDataPrefix);
        if (offset != 0) {
            offset += Gen6Constants.tmDataPrefix.length() / 2; // because it was a prefix
            List<Integer> tms = new ArrayList<>();
            for (int i = 0; i < Gen6Constants.tmBlockOneCount; i++) {
                tms.add(readWord(code, offset + i * 2));
            }
            offset += (Gen6Constants.getTMBlockTwoStartingOffset(romEntry.romType) * 2);
            for (int i = 0; i < (Gen6Constants.tmCount - Gen6Constants.tmBlockOneCount); i++) {
                tms.add(readWord(code, offset + i * 2));
            }
            return tms;
        } else {
            return null;
        }
    }

    @Override
    public List<Integer> getHMMoves() {
        String tmDataPrefix = Gen6Constants.tmDataPrefix;
        int offset = find(code, tmDataPrefix);
        if (offset != 0) {
            offset += Gen6Constants.tmDataPrefix.length() / 2; // because it was a prefix
            offset += Gen6Constants.tmBlockOneCount * 2; // TM data
            List<Integer> hms = new ArrayList<>();
            for (int i = 0; i < Gen6Constants.hmBlockOneCount; i++) {
                hms.add(readWord(code, offset + i * 2));
            }
            if (romEntry.romType == Gen6Constants.Type_ORAS) {
                hms.add(readWord(code, offset + Gen6Constants.rockSmashOffsetORAS));
                hms.add(readWord(code, offset + Gen6Constants.diveOffsetORAS));
            }
            return hms;
        } else {
            return null;
        }
    }

    @Override
    public void setTMMoves(List<Integer> moveIndexes) {
        String tmDataPrefix = Gen6Constants.tmDataPrefix;
        int offset = find(code, tmDataPrefix);
        if (offset > 0) {
            offset += Gen6Constants.tmDataPrefix.length() / 2; // because it was a prefix
            for (int i = 0; i < Gen6Constants.tmBlockOneCount; i++) {
                writeWord(code, offset + i * 2, moveIndexes.get(i));
            }
            offset += (Gen6Constants.getTMBlockTwoStartingOffset(romEntry.romType) * 2);
            for (int i = 0; i < (Gen6Constants.tmCount - Gen6Constants.tmBlockOneCount); i++) {
                writeWord(code, offset + i * 2, moveIndexes.get(i + Gen6Constants.tmBlockOneCount));
            }

            // Update TM item descriptions
            List<String> itemDescriptions = getStrings(false, romEntry.getInt("ItemDescriptionsTextOffset"));
            List<String> moveDescriptions = getStrings(false, romEntry.getInt("MoveDescriptionsTextOffset"));
            // TM01 is item 328 and so on
            for (int i = 0; i < Gen6Constants.tmBlockOneCount; i++) {
                itemDescriptions.set(i + Gen6Constants.tmBlockOneOffset, moveDescriptions.get(moveIndexes.get(i)));
            }
            // TM93-95 are 618-620
            for (int i = 0; i < Gen6Constants.tmBlockTwoCount; i++) {
                itemDescriptions.set(i + Gen6Constants.tmBlockTwoOffset,
                        moveDescriptions.get(moveIndexes.get(i + Gen6Constants.tmBlockOneCount)));
            }
            // TM96-100 are 690 and so on
            for (int i = 0; i < Gen6Constants.tmBlockThreeCount; i++) {
                itemDescriptions.set(i + Gen6Constants.tmBlockThreeOffset,
                        moveDescriptions.get(moveIndexes.get(i + Gen6Constants.tmBlockOneCount + Gen6Constants.tmBlockTwoCount)));
            }
            // Save the new item descriptions
            setStrings(false, romEntry.getInt("ItemDescriptionsTextOffset"), itemDescriptions);
            // Palettes
            String palettePrefix = Gen6Constants.itemPalettesPrefix;
            int offsPals = find(code, palettePrefix);
            if (offsPals > 0) {
                offsPals += Gen6Constants.itemPalettesPrefix.length() / 2; // because it was a prefix
                // Write pals
                for (int i = 0; i < Gen6Constants.tmBlockOneCount; i++) {
                    int itmNum = Gen6Constants.tmBlockOneOffset + i;
                    Move m = this.moves[moveIndexes.get(i)];
                    int pal = this.typeTMPaletteNumber(m.type);
                    writeWord(code, offsPals + itmNum * 4, pal);
                }
                for (int i = 0; i < (Gen6Constants.tmBlockTwoCount); i++) {
                    int itmNum = Gen6Constants.tmBlockTwoOffset + i;
                    Move m = this.moves[moveIndexes.get(i + Gen6Constants.tmBlockOneCount)];
                    int pal = this.typeTMPaletteNumber(m.type);
                    writeWord(code, offsPals + itmNum * 4, pal);
                }
                for (int i = 0; i < (Gen6Constants.tmBlockThreeCount); i++) {
                    int itmNum = Gen6Constants.tmBlockThreeOffset + i;
                    Move m = this.moves[moveIndexes.get(i + Gen6Constants.tmBlockOneCount + Gen6Constants.tmBlockTwoCount)];
                    int pal = this.typeTMPaletteNumber(m.type);
                    writeWord(code, offsPals + itmNum * 4, pal);
                }
            }
        }
    }

    private int find(byte[] data, String hexString) {
        if (hexString.length() % 2 != 0) {
            return -3; // error
        }
        byte[] searchFor = new byte[hexString.length() / 2];
        for (int i = 0; i < searchFor.length; i++) {
            searchFor[i] = (byte) Integer.parseInt(hexString.substring(i * 2, i * 2 + 2), 16);
        }
        List<Integer> found = RomFunctions.search(data, searchFor);
        if (found.size() == 0) {
            return -1; // not found
        } else if (found.size() > 1) {
            return -2; // not unique
        } else {
            return found.get(0);
        }
    }

    @Override
    public int getTMCount() {
        return Gen6Constants.tmCount;
    }

    @Override
    public int getHMCount() {
        return Gen6Constants.getHMCount(romEntry.romType);
    }

    @Override
    public Map<Pokemon, boolean[]> getTMHMCompatibility() {
        Map<Pokemon, boolean[]> compat = new TreeMap<>();
        int formeCount = Gen6Constants.getFormeCount(romEntry.romType);
        for (int i = 1; i <= Gen6Constants.pokemonCount + formeCount; i++) {
            byte[] data;
            data = pokeGarc.files.get(i).get(0);
            Pokemon pkmn = pokes[i];
            boolean[] flags = new boolean[Gen6Constants.tmCount + Gen6Constants.getHMCount(romEntry.romType) + 1];
            for (int j = 0; j < 13; j++) {
                readByteIntoFlags(data, flags, j * 8 + 1, Gen6Constants.bsTMHMCompatOffset + j);
            }
            compat.put(pkmn, flags);
        }
        return compat;
    }

    @Override
    public void setTMHMCompatibility(Map<Pokemon, boolean[]> compatData) {
        for (Map.Entry<Pokemon, boolean[]> compatEntry : compatData.entrySet()) {
            Pokemon pkmn = compatEntry.getKey();
            boolean[] flags = compatEntry.getValue();
            byte[] data = pokeGarc.files.get(pkmn.number).get(0);
            for (int j = 0; j < 13; j++) {
                data[Gen6Constants.bsTMHMCompatOffset + j] = getByteFromFlags(flags, j * 8 + 1);
            }
        }
    }

    @Override
    public boolean hasMoveTutors() {
        return false;
    }

    @Override
    public List<Integer> getMoveTutorMoves() {
        return new ArrayList<>();
    }

    @Override
    public void setMoveTutorMoves(List<Integer> moves) {
        // do nothing for now
    }

    @Override
    public Map<Pokemon, boolean[]> getMoveTutorCompatibility() {
        return new TreeMap<>();
    }

    @Override
    public void setMoveTutorCompatibility(Map<Pokemon, boolean[]> compatData) {
        // do nothing for now
    }

    @Override
    public String getROMName() {
        return "Pokemon " + romEntry.name;
    }

    @Override
    public String getROMCode() {
        return romEntry.romCode;
    }

    @Override
    public String getSupportLevel() {
        return "None";
    }

    @Override
    public boolean hasTimeBasedEncounters() {
        return false;
    }

    @Override
    public List<Pokemon> bannedForStaticPokemon() {
        return Gen6Constants.actuallyCosmeticForms
                .stream()
                .filter(index -> index < Gen6Constants.pokemonCount + Gen6Constants.getFormeCount(romEntry.romType))
                .map(index -> pokes[index])
                .collect(Collectors.toList());
    }

    @Override
    public void removeTradeEvolutions(boolean changeMoveEvos) {
        Map<Integer, List<MoveLearnt>> movesets = this.getMovesLearnt();
        log("--Removing Trade Evolutions--");
        Set<Evolution> extraEvolutions = new HashSet<>();
        for (Pokemon pkmn : pokes) {
            if (pkmn != null) {
                extraEvolutions.clear();
                for (Evolution evo : pkmn.evolutionsFrom) {
                    if (changeMoveEvos && evo.type == EvolutionType.LEVEL_WITH_MOVE) {
                        // read move
                        int move = evo.extraInfo;
                        int levelLearntAt = 1;
                        for (MoveLearnt ml : movesets.get(evo.from.number)) {
                            if (ml.move == move) {
                                levelLearntAt = ml.level;
                                break;
                            }
                        }
                        if (levelLearntAt == 1) {
                            // override for piloswine
                            levelLearntAt = 45;
                        }
                        // change to pure level evo
                        evo.type = EvolutionType.LEVEL;
                        evo.extraInfo = levelLearntAt;
                        logEvoChangeLevel(evo.from.fullName(), evo.to.fullName(), levelLearntAt);
                    }
                    // Pure Trade
                    if (evo.type == EvolutionType.TRADE) {
                        // Replace w/ level 37
                        evo.type = EvolutionType.LEVEL;
                        evo.extraInfo = 37;
                        logEvoChangeLevel(evo.from.fullName(), evo.to.fullName(), 37);
                    }
                    // Trade w/ Item
                    if (evo.type == EvolutionType.TRADE_ITEM) {
                        // Get the current item & evolution
                        int item = evo.extraInfo;
                        if (evo.from.number == Gen6Constants.slowpokeIndex) {
                            // Slowpoke is awkward - he already has a level evo
                            // So we can't do Level up w/ Held Item for him
                            // Put Water Stone instead
                            evo.type = EvolutionType.STONE;
                            evo.extraInfo = Gen6Constants.waterStoneIndex; // water
                            // stone
                            logEvoChangeStone(evo.from.fullName(), evo.to.fullName(), itemNames.get(Gen6Constants.waterStoneIndex));
                        } else {
                            logEvoChangeLevelWithItem(evo.from.fullName(), evo.to.fullName(), itemNames.get(item));
                            // Replace, for this entry, w/
                            // Level up w/ Held Item at Day
                            evo.type = EvolutionType.LEVEL_ITEM_DAY;
                            // now add an extra evo for
                            // Level up w/ Held Item at Night
                            Evolution extraEntry = new Evolution(evo.from, evo.to, true,
                                    EvolutionType.LEVEL_ITEM_NIGHT, item);
                            extraEvolutions.add(extraEntry);
                        }
                    }
                    if (evo.type == EvolutionType.TRADE_SPECIAL) {
                        // This is the karrablast <-> shelmet trade
                        // Replace it with Level up w/ Other Species in Party
                        // (22)
                        // Based on what species we're currently dealing with
                        evo.type = EvolutionType.LEVEL_WITH_OTHER;
                        evo.extraInfo = (evo.from.number == Gen6Constants.karrablastIndex ? Gen6Constants.shelmetIndex
                                : Gen6Constants.karrablastIndex);
                        logEvoChangeLevelWithPkmn(evo.from.fullName(), evo.to.fullName(),
                                pokes[(evo.from.number == Gen6Constants.karrablastIndex ? Gen6Constants.shelmetIndex
                                        : Gen6Constants.karrablastIndex)].fullName());
                    }
                    // TBD: Inkay, Pancham, Sliggoo? Sylveon?
                }

                pkmn.evolutionsFrom.addAll(extraEvolutions);
                for (Evolution ev : extraEvolutions) {
                    ev.to.evolutionsTo.add(ev);
                }
            }
        }
        logBlankLine();
    }

    @Override
    public void removePartyEvolutions() {
        for (Pokemon pkmn : pokes) {
            if (pkmn != null) {
                for (Evolution evo : pkmn.evolutionsFrom) {
                    if (evo.type == EvolutionType.LEVEL_WITH_OTHER) {
                        // Replace w/ level 35
                        evo.type = EvolutionType.LEVEL;
                        evo.extraInfo = 35;
                        log(String.format("%s now evolves into %s at minimum level %d", evo.from.fullName(), evo.to.fullName(),
                                evo.extraInfo));
                    }
                }
            }
        }
        logBlankLine();
    }

    @Override
    public boolean hasShopRandomization() {
        return false;
    }

    @Override
    public boolean canChangeTrainerText() {
        return false;
    }

    @Override
    public List<String> getTrainerNames() {
        return new ArrayList<>();
    }

    @Override
    public int maxTrainerNameLength() {
        return 0;
    }

    @Override
    public void setTrainerNames(List<String> trainerNames) {
        // do nothing for now
    }

    @Override
    public TrainerNameMode trainerNameMode() {
        return TrainerNameMode.MAX_LENGTH;
    }

    @Override
    public List<Integer> getTCNameLengthsByTrainer() {
        return new ArrayList<>();
    }

    @Override
    public List<String> getTrainerClassNames() {
        return new ArrayList<>();
    }

    @Override
    public void setTrainerClassNames(List<String> trainerClassNames) {
        // do nothing for now
    }

    @Override
    public int maxTrainerClassNameLength() {
        return 0;
    }

    @Override
    public boolean fixedTrainerClassNamesLength() {
        return false;
    }

    @Override
    public List<Integer> getDoublesTrainerClasses() {
        return new ArrayList<>();
    }

    @Override
    public String getDefaultExtension() {
        return "cxi";
    }

    @Override
    public int abilitiesPerPokemon() {
        return 3;
    }

    @Override
    public int highestAbilityIndex() {
        return Gen6Constants.getHighestAbilityIndex(romEntry.romType);
    }

    @Override
    public int internalStringLength(String string) {
        return 0;
    }

    @Override
    public void applySignature() {
        // For now, do nothing.
    }

    @Override
    public ItemList getAllowedItems() {
        return null;
    }

    @Override
    public ItemList getNonBadItems() {
        return null;
    }

    @Override
    public List<Integer> getRegularShopItems() {
        return null;
    }

    @Override
    public List<Integer> getOPShopItems() {
        return null;
    }

    @Override
    public String[] getItemNames() {
        return itemNames.toArray(new String[0]);
    }

    @Override
    public String[] getShopNames() {
        return new String[0];
    }

    @Override
    public String abilityName(int number) {
        return abilityNames.get(number);
    }

    @Override
    public List<Integer> getCurrentFieldTMs() {
        return new ArrayList<>();
    }

    @Override
    public void setFieldTMs(List<Integer> fieldTMs) {
        // do nothing for now
    }

    @Override
    public List<Integer> getRegularFieldItems() {
        return new ArrayList<>();
    }

    @Override
    public void setRegularFieldItems(List<Integer> items) {
        // do nothing for now
    }

    @Override
    public List<Integer> getRequiredFieldTMs() {
        return new ArrayList<>();
    }

    @Override
    public List<IngameTrade> getIngameTrades() {
        return new ArrayList<>();
    }

    @Override
    public void setIngameTrades(List<IngameTrade> trades) {
        // do nothing for now
    }

    @Override
    public boolean hasDVs() {
        return false;
    }

    @Override
    public int generationOfPokemon() {
        return 6;
    }

    @Override
    public void removeEvosForPokemonPool() {
        // do nothing for now
    }

    @Override
    public boolean supportsFourStartingMoves() {
        return true;
    }

    @Override
    public List<Integer> getFieldMoves() {
        return new ArrayList<>();
    }

    @Override
    public List<Integer> getEarlyRequiredHMMoves() {
        return new ArrayList<>();
    }

    @Override
    public Map<Integer, List<Integer>> getShopItems() {
        return new TreeMap<>();
    }

    @Override
    public void setShopItems(Map<Integer, List<Integer>> shopItems) {
        // do nothing for now
    }

    @Override
    public void setShopPrices() {
        // do nothing for now
    }

    @Override
    public List<Integer> getMainGameShops() {
        return new ArrayList<>();
    }

    @Override
    public BufferedImage getMascotImage() {
        try {
            GARCArchive pokespritesGARC = this.readGARC(romEntry.getString("PokemonGraphics"),false);
            int pkIndex = this.random.nextInt(pokespritesGARC.files.size()-2)+1;

            byte[] icon = pokespritesGARC.files.get(pkIndex).get(0);
            int paletteCount = readWord(icon,2);
            byte[] rawPalette = Arrays.copyOfRange(icon,4,4+paletteCount*2);
            int[] palette = new int[paletteCount];
            for (int i = 0; i < paletteCount; i++) {
                palette[i] = GFXFunctions.conv3DS16BitColorToARGB(readWord(rawPalette, i * 2));
            }

            int width = 64;
            int height = 32;
            // Get the picture and uncompress it.
            byte[] uncompressedPic = Arrays.copyOfRange(icon,4+paletteCount*2,4+paletteCount*2+width*height);

            int bpp = paletteCount <= 0x10 ? 4 : 8;
            // Output to 64x144 tiled image to prepare for unscrambling
            BufferedImage bim = GFXFunctions.drawTiledZOrderImage(uncompressedPic, palette, 0, width, height, bpp);

            // Unscramble the above onto a 96x96 canvas
            BufferedImage finalImage = new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB);
            Graphics g = finalImage.getGraphics();
            g.drawImage(bim, 0, 0, 64, 64, 0, 0, 64, 64, null);
            g.drawImage(bim, 64, 0, 96, 8, 0, 64, 32, 72, null);
            g.drawImage(bim, 64, 8, 96, 16, 32, 64, 64, 72, null);
            g.drawImage(bim, 64, 16, 96, 24, 0, 72, 32, 80, null);
            g.drawImage(bim, 64, 24, 96, 32, 32, 72, 64, 80, null);
            g.drawImage(bim, 64, 32, 96, 40, 0, 80, 32, 88, null);
            g.drawImage(bim, 64, 40, 96, 48, 32, 80, 64, 88, null);
            g.drawImage(bim, 64, 48, 96, 56, 0, 88, 32, 96, null);
            g.drawImage(bim, 64, 56, 96, 64, 32, 88, 64, 96, null);
            g.drawImage(bim, 0, 64, 64, 96, 0, 96, 64, 128, null);
            g.drawImage(bim, 64, 64, 96, 72, 0, 128, 32, 136, null);
            g.drawImage(bim, 64, 72, 96, 80, 32, 128, 64, 136, null);
            g.drawImage(bim, 64, 80, 96, 88, 0, 136, 32, 144, null);
            g.drawImage(bim, 64, 88, 96, 96, 32, 136, 64, 144, null);

            // Phew, all done.
            return finalImage;
        } catch (IOException e) {
            throw new RandomizerIOException(e);
        }
    }
}
