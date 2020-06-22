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
```
    $ clojure -A:test:runner
```

Build a deployable jar of this library:
```
    $ clojure -A:jar
```

Install it locally:
```
    $ clojure -A:install
```

Deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment variables:
```
    $ clojure -A:deploy
```
## License

The MIT License (MIT)

Copyright © 2020 Avisi Apps B.V.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
