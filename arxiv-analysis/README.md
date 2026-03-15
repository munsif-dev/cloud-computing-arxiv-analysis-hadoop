# ArXiv Research Field Activity Analysis
### MapReduce Assignment — EE7222/EC7204 Cloud Computing

Analyzes ~2.2 million ArXiv academic paper records using Hadoop MapReduce to compute, per research category:

| Metric | Description |
|---|---|
| `total_papers` | Number of papers in that category |
| `avg_versions` | Average number of revisions per paper |
| `avg_abstract_words` | Average abstract length (word count) |
| `avg_authors` | Average number of co-authors |

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| WSL2 (Ubuntu 22.04) | — | Required on Windows |
| Java (JDK) | 11 | `openjdk-11-jdk` |
| Apache Hadoop | 3.3.6 | Pseudo-distributed single-node |
| Apache Maven | 3.8+ | For building the JAR |
| Kaggle CLI | latest | For dataset download |

---

## Part 1 — Hadoop Setup (WSL2)

Open a WSL2 terminal and run all commands below.

### 1.1 Install Java, SSH, Maven
```bash
sudo apt update && sudo apt install -y openjdk-11-jdk wget ssh maven
java -version   # must show openjdk 11
```

### 1.2 Configure Passwordless SSH (required by Hadoop)
```bash
ssh-keygen -t rsa -P "" -f ~/.ssh/id_rsa
cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
sudo service ssh start
ssh localhost exit   # must connect without a password prompt
```

### 1.3 Download and Install Hadoop 3.3.6
```bash
cd ~
wget https://downloads.apache.org/hadoop/common/hadoop-3.3.6/hadoop-3.3.6.tar.gz
tar -xzf hadoop-3.3.6.tar.gz
sudo mv hadoop-3.3.6 /opt/hadoop
```

### 1.4 Set Environment Variables
Add the following lines to `~/.zshrc` (or `~/.bashrc` if using bash):
```bash
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export HADOOP_HOME=/opt/hadoop
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
export PATH=$PATH:$HADOOP_HOME/bin:$HADOOP_HOME/sbin
```
Then reload:
```bash
source ~/.bashrc
hadoop version   # must print Hadoop 3.3.6
```

### 1.5 Configure Hadoop (pseudo-distributed mode)

**`/opt/hadoop/etc/hadoop/hadoop-env.sh`** — add this line anywhere:
```bash
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
```

**`/opt/hadoop/etc/hadoop/core-site.xml`**:
```xml
<configuration>
    <property>
        <name>fs.defaultFS</name>
        <value>hdfs://localhost:9000</value>
    </property>
</configuration>
```

**`/opt/hadoop/etc/hadoop/hdfs-site.xml`**:
```xml
<configuration>
    <property><name>dfs.replication</name><value>1</value></property>
    <property><name>dfs.namenode.name.dir</name><value>/opt/hadoop/data/namenode</value></property>
    <property><name>dfs.datanode.data.dir</name><value>/opt/hadoop/data/datanode</value></property>
</configuration>
```

**`/opt/hadoop/etc/hadoop/mapred-site.xml`**:
```xml
<configuration>
    <property><name>mapreduce.framework.name</name><value>yarn</value></property>
    <property>
        <name>mapreduce.application.classpath</name>
        <value>$HADOOP_MAPRED_HOME/share/hadoop/mapreduce/*:$HADOOP_MAPRED_HOME/share/hadoop/mapreduce/lib/*</value>
    </property>
</configuration>
```

**`/opt/hadoop/etc/hadoop/yarn-site.xml`**:
```xml
<configuration>
    <property><name>yarn.nodemanager.aux-services</name><value>mapreduce_shuffle</value></property>
    <property>
        <name>yarn.nodemanager.env-whitelist</name>
        <value>JAVA_HOME,HADOOP_COMMON_HOME,HADOOP_HDFS_HOME,HADOOP_CONF_DIR,CLASSPATH_PREPEND_DISTCACHE,HADOOP_YARN_HOME,HADOOP_HOME,PATH,LANG,TZ,MALLOC_ARENA_MAX</value>
    </property>
</configuration>
```

### 1.6 Format NameNode and Start the Cluster
```bash
mkdir -p /opt/hadoop/data/namenode /opt/hadoop/data/datanode
hdfs namenode -format          # run ONCE — do not repeat after first format
start-dfs.sh
start-yarn.sh
jps   # expect 5 processes: NameNode, DataNode, SecondaryNameNode, ResourceManager, NodeManager
```

Web UIs (open in Windows browser):
- HDFS: http://localhost:9870
- YARN: http://localhost:8088

> **Note**: WSL2 does not persist running processes across Windows restarts.
> After each reboot, re-run `sudo service ssh start && start-dfs.sh && start-yarn.sh`.

---

## Part 2 — Dataset Download

Install the Kaggle CLI and place your `kaggle.json` API token in `~/.kaggle/`:
```bash
pip install kaggle
mkdir -p ~/.kaggle
# Copy your kaggle.json to ~/.kaggle/kaggle.json
chmod 600 ~/.kaggle/kaggle.json
```

Download the ArXiv metadata snapshot:
```bash
mkdir -p ~/data
cd ~/data
kaggle datasets download -d Cornell-University/arxiv
unzip arxiv.zip
# The main file is: arxiv-metadata-oai-snapshot.json (~4 GB uncompressed)
wc -l arxiv-metadata-oai-snapshot.json   # should print ~2,200,000
```

---

## Part 3 — Build the JAR

```bash
cd /path/to/arxiv-analysis   # folder containing pom.xml
mvn clean package -DskipTests
# Output fat JAR: target/arxiv-analysis.jar
ls -lh target/arxiv-analysis.jar
```

---

## Part 4 — Upload Dataset to HDFS

```bash
# Create input directory in HDFS
hdfs dfs -mkdir -p /user/hadoop/arxiv/input

# Upload the dataset (~4 GB — takes a few minutes)
hdfs dfs -put ~/data/arxiv-metadata-oai-snapshot.json /user/hadoop/arxiv/input/

# Verify
hdfs dfs -ls /user/hadoop/arxiv/input/
```

---

## Part 5 — Run the MapReduce Job

```bash
hadoop jar target/arxiv-analysis.jar \
    com.arxivanalysis.ArxivAnalysis \
    /user/hadoop/arxiv/input \
    /user/hadoop/arxiv/output
```

Monitor progress in the YARN UI at http://localhost:8088.

---

## Part 6 — Retrieve and Save Results

```bash
# Print results to terminal
hdfs dfs -cat /user/hadoop/arxiv/output/part-r-00000

# Save a local copy (commit this to the repo as evidence)
hdfs dfs -get /user/hadoop/arxiv/output/part-r-00000 ./sample_output/part-r-00000
```

To re-run the job (output directory must not already exist):
```bash
hdfs dfs -rm -r /user/hadoop/arxiv/output
```

---

## Project Structure

```
arxiv-analysis/
├── pom.xml                          Maven build file
├── README.md                        This file
├── sample_output/
│   └── part-r-00000                 Actual output from the full run
├── screenshots/                     Evidence: jps, HDFS UI, YARN UI, output
└── src/main/java/com/arxivanalysis/
    ├── ArxivMapper.java             Parses JSON, emits (category, counts)
    ├── ArxivReducer.java            Aggregates counts, computes averages (also Combiner)
    └── ArxivAnalysis.java           Job driver
```

---

## Expected Output (sample)

```
cs.AI        total_papers=87234    avg_versions=1.93   avg_abstract_words=148   avg_authors=4.1
cs.CV        total_papers=112489   avg_versions=2.11   avg_abstract_words=155   avg_authors=4.8
cs.LG        total_papers=152341   avg_versions=2.04   avg_abstract_words=147   avg_authors=4.3
math.ST      total_papers=43218    avg_versions=1.62   avg_abstract_words=162   avg_authors=2.1
physics.hep-th  total_papers=98123  avg_versions=1.41  avg_abstract_words=135   avg_authors=3.7
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `ssh: connect to host localhost port 22: Connection refused` | Run `sudo service ssh start` |
| `JAVA_HOME not set` during `start-dfs.sh` | Add `export JAVA_HOME=...` to `/opt/hadoop/etc/hadoop/hadoop-env.sh` |
| `Output directory already exists` | Run `hdfs dfs -rm -r /user/hadoop/arxiv/output` then retry |
| `ClassNotFoundException: com.arxivanalysis.ArxivAnalysis` | Ensure you built with `mvn clean package` and the JAR is in `target/` |
| WSL2 loses HDFS data after Windows restart | HDFS data persists in `/opt/hadoop/data` — just restart daemons |
