# Dig migration — set `portfolioKind: PAPER` on legacy paper books

After deploying PAPER kind, re-kind books that were stored as `BROKER`.

```js
const coll = db.getCollection("portfolios");
const filter = {
  $and: [
    { $or: [{ name: { $regex: /^paper$/i } }, { tags: "PAPER" }] },
    { $or: [{ portfolioKind: { $exists: false } }, { portfolioKind: null }, { portfolioKind: "BROKER" }] },
    { portfolioKind: { $nin: ["BASKET", "DELETED", "PAPER"] } },
  ],
};
const result = coll.updateMany(filter, { $set: { portfolioKind: "PAPER" } });
printjson({ matched: result.matchedCount, modified: result.modifiedCount });
```

Run via `mongosh` against dig Mongo `portfolios` collection.
