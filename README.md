# Chiyoko: Player RNG

Chiyoko Player cracks the player's RNG and predicts enchanting table and anvil outcomes before they happen by simulating the RNG exactly as minecraft does.

- loot prediction (wither skeletons, fishing, bartering, vaults and more) lives in [Chiyoko: Loot Visualiser](https://github.com/mimikyumochi/chiyoko-loot)

---

## Features

### Player RNG Cracking
- cracks the player's entity RNG from the velocity of items you drop, no enchanting table or xp needed
- usually takes **3-4 drops** while standing still, although it can take more
- keeps tracking the RNG through further drops, enchanting and anvil use
- automatically resets when something might have used the RNG (moving, taking damage, hunger, xp changes, using items, respawning)
- detects desyncs and re-cracks from the next drop

### Enchantment Prediction
- predicts enchantment table results using the data provided when hovering an enchantment option
- can search for desired enchantments with the `/predict` command

### Anvil Prediction
- shows whether the anvil will chip on the next use, above the anvil screen
- tells you how many uses are safe, or how many items to drop to skip a chip

### Multiplayer Support
- detects servers that share entity RNG (paper) and tells you instead of failing silently

---

## Supported Predictions

| source | prediction |
|--------|------------|
| enchanting table | enchantments on each slot |
| enchanting table | item drops and bookshelves needed for chosen enchants |
| anvil | uses until chip, drops needed to skip a chip |

---

## Commands

### `/predict <item> <enchant1> <level1> [<enchant2> <level2>] [<enchant3> <level3>]`
searches ahead using the cracked player RNG to find how many item drops and bookshelves are required to obtain the desired enchantments on the given item.

supports up to **three** enchantment/level pairs

runs in the background so the game doesnt freeze, and gives up after 10 seconds

---

## How To Use

1. stand still and dont turn your camera
2. drop items one at a time until the overlay says the seed is cracked
3. run `/predict` or open an anvil
4. follow the steps without moving or taking damage

---

## Limitations

- does not work on servers that share entity RNG, such as **paper** and its forks
- you have to stand still while cracking and following a prediction
- turning your camera right before a drop makes that drop count less towards cracking
- anvil prediction does nothing in creative since creative anvils never chip

---

## Known Issues

- after enchanting, the enchantment table tooltips wait for the new enchanting seed to be re-cracked from the table

---

## Support

if you have any issues please message me on discord (`mimikyumochi`) or send me a dm on twitter ([@mimikyumochii](https://twitter.com/mimikyumochii))
