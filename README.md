# crux-datastore

## Usage

### Use in your project

If you want to start using [Crux](https://opencrux.com/) make sure to first follow
the [offical documentation](https://opencrux.com/docs#config-nodes)  about configuring nodes.

To start using Datastore you need to setup two arguments

| Property | Description | Required? |
| --- | --- | --- |
| `:avisi.crux.datastore/datastore` | An instance of com.google.cloud.datastore.Datastore |  yes   |
| `:avisi.crux.datastore/namespace` | Specify a namespace for your data. |  yes   |

#### Datastore with in memory KV store configuration
Simple datastore configuration without a persistent KV store implementation:
```clojure
{:crux.node/topology '[avisi.crux.datastore/topology]
 :avisi.crux.datastore/datastore *datastore*
 :avisi.crux.datastore/namespace "my-namespace"}
```

#### Datastore with in RocksDB KV store configuration
Simple datastore configuration without a persisten KV store implementation:
```clojure
{:crux.node/topology '[avisi.crux.datastore/topology
                       crux.kv.rocksdb/kv-store]
 :avisi.crux.datastore/datastore *datastore*
 :avisi.crux.datastore/namespace "my-namespace"}
```

## Development

Run test

    $ clojure -A:test:runner


Build a deployable jar of this library:

    $ clojure -A:jar

Install it locally:

    $ clojure -A:install

Deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment variables:

    $ clojure -A:deploy

## License

Copyright © 2020 Mitsel

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
