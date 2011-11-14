<%@ include file="includes/header.jsp" %>


<%@page import="java.util.List"%>
<%@page import="org.nuxeo.wizard.download.PendingDownload"%><h1><fmt:message key="label.packagesDownload" /></h1>

<%
String baseUrl = ctx.getBaseUrl();
if (baseUrl==null) {
    baseUrl="/nuxeo/";
}
boolean needDownload = false;
List<DownloadPackage> packages = PackageDownloader.instance().getSelectedPackages();
List<PendingDownload> downloads = PackageDownloader.instance().getPendingDownloads();
boolean downloadStarted = PackageDownloader.instance().isDownloadStarted();
boolean downloadCompleted = PackageDownloader.instance().isDownloadCompleted();
boolean downloadInProgress = PackageDownloader.instance().isDownloadInProgress();
%>

<%@ include file="includes/form-start.jsp" %>
<span class="screenDescription">
<fmt:message key="label.packagesDownload.description" /> <br/>
</span>

<% if (!downloadStarted) { %>
<table>
<tr><td>
<span class="screenExplanations">
<fmt:message key="label.packagesDownload.selectedPackages" /> <br/>
</span>
<ul>
<%for (DownloadPackage pkg : packages) {%>
  <li><%=pkg.getFilename()%> &nbsp;
  <%if (pkg.isAlreadyInLocal()) {%>
    (already in local)
  <%} else {
     needDownload = true;%>
    (to be downloaded)
  <%} %>
  </li>
<%}%>
</ul>
</td>
<td>
<input style="display:none" type="button" class="glossyButton" id="btnDownload" value="<fmt:message key="label.action.downloadStart"/>"  onclick="navigateTo('<%=currentPage.getAction()%>?startDownload=true');"/>
</td>
</tr>

</table>
<% }%>

<script type="text/javascript">

function refreshDownloadTable() {
  $("#downloadTable").delay(500).load('<%=currentPage.getAction()%> #downloadTable', function() {
      if ($("#downloadInProgress").html()=="true") {
      refreshDownloadTable();
      }
      else if ($("#downloadCompleted").html()=="true") {
        $("#btnNext").css("display","inline");
      }
    });
}

$(document).ready(function(){
<%if (needDownload) {%>
   $("#btnDownload").css("display","inline");
<% };
  if (downloadCompleted) {%>
   $("#btnNext").css("display","inline");
<%};
  if (downloadInProgress) {%>
  refreshDownloadTable();
<%}%>
});
</script>

<% if (downloadStarted) { %>
<div id="downloadTable">
<table>
<%for (PendingDownload dw : downloads) {%>
  <tr>
     <td> <%=dw.getPkg().getFilename() %></td>
     <td>
     <%
     switch (dw.getStatus()) {
         case PendingDownload.PENDING:
             %><fmt:message key="label.downloadStatus.PENDING"/><%
             break;
         case PendingDownload.INPROGRESS:
             %><fmt:message key="label.downloadStatus.INPROGRESS"/><%
             break;
         case PendingDownload.VERIFICATION:
             %><fmt:message key="label.downloadStatus.VERIFICATION"/><%
             break;
         case PendingDownload.VERIFIED:
             %><fmt:message key="label.downloadStatus.VERIFIED"/>
             <img src="<%=contextPath%>/images/pkgok.png" height="18"/><%
             break;
         case PendingDownload.ABORTED:
             %><fmt:message key="label.downloadStatus.ABORTED"/><%
             break;
         case PendingDownload.COMPLETED:
             %><fmt:message key="label.downloadStatus.COMPLETED"/><%
             break;
         case PendingDownload.CORRUPTED:
             %><fmt:message key="label.downloadStatus.CORRUPTED"/>
             <img src="<%=contextPath%>/images/broken.png" height="18"/><%
             break;
     }%>
     </td>
     <td>
       <div style="width:200px:height:10px">
       <%
       switch (dw.getStatus()) {
          case PendingDownload.PENDING:
              %>
              <img src="<%=contextPath%>/images/pause.png" height="18" title="Pending"/>
              <%
              break;
          case PendingDownload.ABORTED:
          case PendingDownload.CORRUPTED:
              %>
              <A href="#" onclick="navigateTo('<%=currentPage.getAction()%>?reStartDownload=<%=dw.getPkg().getId()%>');">Retry download</A>
              <%
              break;
          default:
              %>
         <div style="background-color:green;height:10px;width:<%=2*dw.getProgress()%>px"></div>
              <%
       }
       %>
       </div>
      </td>
  </tr>
<%}%>
</table>
<div style="display:none" id="downloadInProgress"><%=downloadInProgress%></div>
<div style="display:none" id="downloadCompleted"><%=downloadCompleted%></div>
</div>
<%}%>

  </td></tr></table>
  </div>

 </div>
 <center>
 <input type="button" class="glossyButton" id="btnPrev" value="<fmt:message key="label.action.prev"/>" onclick="navigateTo('<%=currentPage.prev().getAction()%>');"/>
 <input style="display:none" type="submit" class="glossyButton" id="btnNext" value="<fmt:message key="label.action.next"/>"/>
 </center>

</form>
<%@ include file="includes/footer.jsp" %>