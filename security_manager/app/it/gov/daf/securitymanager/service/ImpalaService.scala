package it.gov.daf.securitymanager.service

import com.cloudera.impala.jdbc41.DataSource
import com.google.inject.{Inject, Singleton}
import it.gov.daf.common.sso.common.CacheWrapper
import it.gov.daf.securitymanager.service.utilities.ConfigReader
import play.api.Logger


@Singleton
class ImpalaService @Inject()(implicit val cacheWrapper:CacheWrapper){

  private val ds:DataSource = new DataSource()
  private val jdbcString = s"jdbc:impala://${ConfigReader.impalaServer};SSL=1;SSLKeyStore=${ConfigReader.impalaKeyStorePath};SSLKeyStorePwd=${ConfigReader.impalaKeyStorePwd};CAIssuedCertNamesMismatch=1;AuthMech=3"
  Logger.logger.debug(s"jdbcString: $jdbcString")
  ds.setURL(jdbcString)


  def createGrant(tableName:String, groupName:String, permission:String):Either[String,String]={

    val permissionOnQuery = if(permission == Permission.read.toString) "SELECT"
                            else "INSERT"


    val roleName = s"${groupName}_group_role"
    val query = s"GRANT $permissionOnQuery ON TABLE $tableName TO ROLE $roleName"

    executeUpdate(query)
    Right("Grant created")
    //else Left("Can not create Impala grant")
  }


  def revokeGrant(tableName:String, groupName:String, permission:String):Either[String,String]={

    val permissionOnQuery = if(permission == Permission.read.toString) "SELECT"
    else "INSERT"

    val roleName = s"${groupName}_group_role"
    val query = s"REVOKE $permissionOnQuery ON TABLE $tableName FROM ROLE $roleName"

    executeUpdate(query)
    Right("Grant revoked")
    //else Left("Can not revoke Impala grant")
  }

  def revokeGrant(tableName:String, groupName:String):Either[String,String]={

    val roleName = s"${groupName}_group_role"

    executeUpdates( Seq(s"REVOKE SELECT ON TABLE $tableName FROM ROLE $roleName",
                        s"REVOKE INSERT ON TABLE $tableName FROM ROLE $roleName") )

    Right("Grants revoked")
    //else Left("Can not revoke Impala grant")
  }


  def createRole(name:String, isUser:Boolean):Either[String,String]={

    val roleName =  if(isUser) s"${name}_user_role"
                    else s"${name}_group_role"

    val query = s"CREATE ROLE $roleName"
    val query2 = s"GRANT ROLE $roleName TO GROUP $name"

    executeUpdatesAsAdmin(Seq(query,query2))

    Right("Role created")

    //if( executeUpdatesAsAdmin(Seq(query,query2)).filter(_>0).length >=2 ) Right("Role created")
    //else Left("Can not create Impala role")
  }

  def deleteRole(name:String, isUser:Boolean):Either[String,String]={

    val roleName =  if(isUser) s"${name}_user_role"
    else s"${name}_group_role"

    val query = s"REVOKE ROLE $roleName FROM GROUP $name"
    val query2 = s"DROP ROLE $roleName"

    executeUpdatesAsAdmin(Seq(query,query2))

    Right("Role dropped")

    //if( executeUpdatesAsAdmin(Seq(query,query2)).filter(_>0).length >=2 ) Right("Role dropped")
    //else Left("Can not drop Impala role")
  }



  private def executeUpdate(query:String):Int={

    val loginInfo = readLoginInfo

    Logger.logger.debug("Impala connection request")

    val conn = ds.getConnection(loginInfo.user,loginInfo.password)

    Logger.logger.debug("Impala connection obtained")
    Logger.logger.debug(s" Impala update query : $query")

    val stmt = conn.createStatement()
    val res = stmt.executeUpdate(query)

    Logger.logger.debug("Impala query executed")

    conn.close()
    res

  }

  private def executeUpdatesAsAdmin(query:Seq[String]):Seq[Int]={

    Logger.logger.debug("Impala connection request")

    val conn = ds.getConnection(ConfigReader.impalaAdminUser,ConfigReader.impalaAdminUserPwd)

    Logger.logger.debug("Impala connection obtained")

    val res = query.map{ q =>
      Logger.logger.debug(s" Impala update query : $q")
      val stmt = conn.createStatement()
      val out = stmt.executeUpdate(q)
      Logger.logger.debug("Impala query executed")
      out
    }

    conn.close()
    res

  }

  private def executeUpdates(query:Seq[String]):Seq[Int]={

    val loginInfo = readLoginInfo

    Logger.logger.debug("Impala connection request")

    val conn = ds.getConnection(loginInfo.user,loginInfo.password)

    Logger.logger.debug("Impala connection obtained")

    val res = query.map{ q =>
      Logger.logger.debug(s" Impala update query : $q")
      val stmt = conn.createStatement()
      val out = stmt.executeUpdate(q)
      Logger.logger.debug("Impala query executed")
      out
    }

    conn.close()
    res

  }


  // for testing pourpose
  private def executeQuery(query:String):scala.collection.mutable.Map[String,String] ={

    val loginInfo = readLoginInfo
    val conn = ds.getConnection(loginInfo.user,loginInfo.password)

    val stmt = conn.createStatement()
    val rs = stmt.executeQuery(query) //for example "SHOW ROLE GRANT GROUP default_org;"

    val metadata = rs.getMetaData
    val columns = metadata.getColumnCount
    var resultMap = scala.collection.mutable.Map[String,String]()
    while( rs.next() ) {
      for(i <- 0 to columns-1)
        resultMap += metadata.getColumnName(i) -> rs.getString(i)
    }

    conn.close()
    resultMap

  }

}
