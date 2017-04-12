## JsonTweaker

Simply add or remove recipes from the game using json config files.

#### Recipe format

Items names follow this syntax: `<domain:item_name:meta>` eg: `<minecraft:clay>`

Also works with ore dictionary as ore domain.

```json
{
  "shaped": [
    {
      "output": "<minecraft:web>",
      "input": [
        [
          "<minecraft:string>",
          "",
          "<minecraft:string>"
        ],
        [
          "",
          "<minecraft:slime_ball>",
          ""
        ],
        [
          "<minecraft:string>",
          "",
          "<minecraft:string>"
        ]
      ]
    }
  ],
  "remove": ["<minecraft:anvil>"]
}
```

