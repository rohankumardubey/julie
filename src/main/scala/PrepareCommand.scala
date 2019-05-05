

trait TopologyCommand {

  def execute(topology: Projects);
}

class PrepareCommand extends TopologyCommand {


  override def execute(topology: Projects): Unit = {


    val cmds: Array[ACLCommand] = topology
      .projects
      .flatMap { project: Project =>
        new ACL(
          consumers = project.users.consumers,
          producers = project.users.producers,
          connectors = project.users.connectors,
          streams = project.users.streams
        ).build(
          group = "projects",
          projectName = project.name,
          topics = project.topics.map(topic => s"projects.${project.name}.${topic.name}"),
          zookeepers = project.zookeepers.getOrElse(Array("localhost:2181"))
        )
      }

    printCommands(cmds)
  }

  private def printCommands(cmds: Array[ACLCommand]): Unit = {
    cmds.foreach { cmd: ACLCommand =>

      cmd match {
        case _:ConsumerACLCommand => {
          println("# Consumers: ")
        }
        case _:ProducerACLCommand => {
          println("# Producers: ")
        }
        case _:KafkaStreamsACLCommand => {
          println("# Kafka Streams: ")
        }
        case _:ConnectACLCommand => {
          println("# Kafka Connect:")
        }
      }

      println(cmd)
      println
    }
  }
}

