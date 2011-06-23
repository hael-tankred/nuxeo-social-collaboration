<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
<title></title>
<link href="${skinPath}/css/create_document.css" rel="stylesheet" type="text/css">
</head>
<body>
<form class="createDocument" action="${This.path}/createFolder" method="post" enctype="application/x-www-form-urlencoded" target="_parent">
<h3>Add a folder in ${currentDoc.title} </h3>
<table>
<tr>
<td>Title</td>
<td><input class="border input" type="text" name="dc:title" /></td>
</tr>
<tr>
<td>Description</td>
<td><textarea class="border input" name="dc:description" rows="5"></textarea></td>
</tr>
</table>
<div class="actions">
<input class="border" type="submit" name="createFolder" value="Create" onclick="parent.submitForm(this);return false;"/>
<button class="border" name="cancel" value="Cancel" type="button" onclick="parent.jQuery.fancybox.close()">Cancel</button>
<input type="hidden" name="doctype" value="Folder" />
<input type="hidden" name="docRef" value="${currentDoc.id}" />
</div>

</form>
</body>
</html>
